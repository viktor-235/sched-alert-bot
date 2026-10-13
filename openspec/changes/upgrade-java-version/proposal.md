# Proposal

## Why

The project is pinned to Java 21 in four places: the Gradle toolchain, the Docker base image, and the CI and release workflows. We want to run on the newest Java release to get current language features, runtime improvements and security fixes, and to avoid the next base-image removal catching us on an old line (as happened when `openjdk:21-jdk-slim` was removed from Docker Hub). The target version is **not fixed in this proposal**. It is the latest GA Java release available on the day the change is implemented.

## What Changes

- Determine the target Java version at implementation time: the newest GA feature release for which the Eclipse Temurin JDK and JRE images (amd64 + arm64) are published.
- Raise the Gradle toolchain from 21 to the target version.
- Raise the Docker base image from `eclipse-temurin:21-jre` to `eclipse-temurin:<target>-jre`.
- Raise `actions/setup-java` in `ci.yml` and `release.yml` to the target version.
- Upgrade build tooling and libraries as far as the target version requires: Gradle wrapper (8.8 can only run on Java ≤ 22), Spring Boot, Lombok, MapStruct, Testcontainers, and the test stack (Mockito/Byte Buddy).
- Keep the Java version in one place (`.java-version`) so the Gradle toolchain and CI read it from the same source.
- Mention the required Java version in the README prerequisites.

No user-visible behavior changes. No event statuses or Telegram users are affected: new, changed, live, finished and canceled notifications must keep working exactly as they do now, and the existing integration tests guard this. No MongoDB schema change and no Mongock migration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a build and runtime upgrade with no spec-level behavior change (`skip_specs: true`).

## Impact

- `build.gradle` (toolchain, plugin and dependency versions), `gradle/wrapper/*`
- `Dockerfile`
- `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- `README.md`
- Deployment: the new image runs a different JRE. Check it on the test stand via an RC (`CONTRIBUTING.md` §9) before the stable release.
- Versioning: MINOR or PATCH bump. No config or data incompatibility is expected; if Spring Boot moves to a new major line and breaks config properties, the version bump has to account for that.
