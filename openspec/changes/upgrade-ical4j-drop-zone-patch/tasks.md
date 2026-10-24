# Tasks

> **Blocked** until an ical4j release containing [PR #955](https://github.com/ical4j/ical4j/pull/955) (fix for [#903](https://github.com/ical4j/ical4j/issues/903)) is on Maven Central. Check: `gh release list -R ical4j/ical4j -L 3`.

## 1. Confirm the release

- [ ] 1.1 Find the first ical4j release after 4.4.0 and confirm it contains PR #955 (design decision 1): the release notes list #955 or #903, or `gh api repos/ical4j/ical4j/contents/src/main/java/net/fortuna/ical4j/model/ZoneRulesBuilder.java?ref=ical4j-<version>` shows the PR #955 guard in `buildTransitionRules`. Verify: the release version and the evidence are noted for the commit/PR body. If the fix is missing, stop; the change stays blocked.

## 2. Upgrade and remove the patch

- [ ] 2.1 Bump `org.mnode.ical4j:ical4j` in `build.gradle` to the release from 1.1. Verify: `./gradlew dependencies --configuration runtimeClasspath` shows the new version and still no groovy, caffeine or jparsec; `./gradlew compileJava` passes.
- [ ] 2.2 Delete `src/main/java/net/fortuna/ical4j/model/ZoneRulesBuilder.java`, `src/test/java/net/fortuna/ical4j/model/ZoneRulesBuilderPatchTest.java` and the now empty `net/fortuna/...` directories under `src/main` and `src/test`. Remove `compileOnly 'org.jspecify:jspecify:1.0.0'` and its comment from `build.gradle`. Verify: `grep -rn -e jspecify -e net.fortuna.ical4j.model.ZoneRulesBuilder src build.gradle` finds nothing; `./gradlew compileJava compileTestJava` passes.
- [ ] 2.3 Run `./gradlew test --tests '*IcsWriterTest'` (design decision 2). Verify: the case expecting `DTSTART;TZID=Europe/Moscow:20261025T150000` passes without the copy, so the upstream fix is in effect. If it yields `...T160000`, the release does not fix #903: revert 2.1–2.2 and stay blocked.
- [ ] 2.4 If the golden-file test for `src/test/resources/calendar/events-moscow.ics` fails, regenerate the file from the new jar (design decision 3). Verify: `git diff` of the golden file touches only lines inside `BEGIN:VTIMEZONE`…`END:VTIMEZONE`; `IcsWriterTest` passes.

## 3. Final check

- [ ] 3.1 Run `./gradlew test`. Verify: all tests pass.

## Workflow follow-up

- Ship as a PATCH release (`CONTRIBUTING.md` §8).
- Archive the change before merge.
