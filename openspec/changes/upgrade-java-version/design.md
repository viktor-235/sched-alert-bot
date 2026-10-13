# Design

## Context

Java 21 is set independently in `build.gradle` (`JavaLanguageVersion.of(21)`), `Dockerfile` (`eclipse-temurin:21-jre`), and the two workflows (`java-version: '21'`). The build runs on Gradle 8.8 with Spring Boot 3.4.1, Lombok (managed by Boot), MapStruct 1.6.3 and Testcontainers 1.21.4. Annotation processors (Lombok, MapStruct) and bytecode tooling (Byte Buddy via Mockito, Gradle itself) are what usually break on a new JDK, not the application code.

## Goals / Non-Goals

**Goals:**
- Build, test and run on the latest GA Java at implementation time.
- Make the next Java bump a one-line change plus the Docker image.

**Non-Goals:**
- Adopting new language features or refactoring code to use them.
- Upgrading dependencies beyond what the target Java requires (no opportunistic upgrades).
- Changing the image layout (jlink/custom runtime, distroless, non-root user).

## Decisions

### Target = latest GA, not latest LTS
The request is the newest version. Non-LTS releases get updates for only six months, so a non-LTS target commits us to bumping again at each release (every March and September), or to going back to the previous LTS.
- Alternative: latest LTS. It is more stable but can lag by up to two years. Not chosen, but the implementer records the LTS status of the chosen version in the commit/PR body.
- Rule: if the latest GA has no published `eclipse-temurin:<N>-jre` image for both amd64 and arm64/v8 yet, use the previous version that has one. Check with `docker buildx imagetools inspect`.

### Single source: `.java-version`
Add a `.java-version` file containing the major version. `actions/setup-java` reads it via `java-version-file`, and `build.gradle` reads it for the toolchain (`JavaLanguageVersion.of(file('.java-version').text.trim())`).
- Alternative: a `gradle.properties` property. It is not readable by `setup-java` without an extra step.
- The `Dockerfile` keeps a literal tag (`FROM` with a build arg would need the release workflow to pass it). The release workflow's Docker build would catch a mismatch only indirectly, so the tasks include a check that `java -version` in the built image matches `.java-version`.

### Dependency upgrades driven by compatibility
Raise each component to the minimum version that officially supports the target Java:
- Gradle wrapper: check the Gradle compatibility matrix for running on the target JDK. The toolchain needs Gradle ≥ the version that supports the target for compiling and testing. Since CI runs Gradle on the same JDK, the matrix's "running Gradle" column decides.
- Spring Boot: the latest patch of the current minor first; a new minor/major only if the current line does not support the target Java.
- Lombok: Boot's managed version, or an explicit override if Lombok's changelog shows target support only in a newer release.
- MapStruct, Testcontainers, Mockito/Byte Buddy: upgrade only if the build or tests fail on the target.

## Risks / Trade-offs

- [Lombok or Byte Buddy not yet supporting the target JDK] → fall back to the previous Java version (same rule as the missing-image case) and note it in the PR.
- [Spring Boot major upgrade needed (e.g. 3 → 4) with config property renames] → `./gradlew test` plus the RC on the test stand catch startup failures. If it requires a config change for the user, treat it as a separate change; this one stays limited to the Java bump.
- [Non-LTS goes out of support in six months] → accepted. The single version source makes the next bump cheap.
- [Different JRE in production] → RC + test-stand check per `CONTRIBUTING.md` §9 before stable.

## Migration Plan

Build-only change, rolled out via the normal release flow: RC image → test stand → stable tag. Rollback: redeploy the previous image tag. No data migration.

## Open Questions

- Exact target version and library versions are resolved at implementation time by the rules above.
