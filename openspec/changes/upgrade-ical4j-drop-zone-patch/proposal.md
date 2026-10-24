# Proposal

## Why

The calendar export carries a patched copy of ical4j's `ZoneRulesBuilder` (`src/main/java/net/fortuna/ical4j/model/ZoneRulesBuilder.java`) as a workaround for [ical4j#903](https://github.com/ical4j/ical4j/issues/903): without it, zones that abolished DST keep a yearly DST rule and Europe/Moscow events are written one hour late from every last Sunday of March. The fix was merged upstream in [PR #955](https://github.com/ical4j/ical4j/pull/955) (into `develop`, 2026-10-23), but it is not in a release yet: the latest is 4.4.0, which the patch is based on. Once a release contains the fix, the copy of a third-party class is dead weight that has to be re-checked on every upgrade, so it should go.

**Blocked** until an ical4j release containing PR #955 is published to Maven Central. Do not start implementing before that.

## What Changes

- Upgrade `org.mnode.ical4j:ical4j` from 4.4.0 to the first release containing PR #955 (expected 4.4.1 or 4.5.0).
- Delete the patched `ZoneRulesBuilder` copy and `ZoneRulesBuilderPatchTest`.
- Remove `compileOnly 'org.jspecify:jspecify:1.0.0'` from `build.gradle`; only the patched copy uses it.
- Update the bundled `VTIMEZONE` in the Moscow golden file if the new release ships a changed tzurl.org definition.

No user-visible behavior changes. No event statuses or Telegram users are affected: notifications don't use ical4j. Subscribers of the published `.ics` files keep getting the same times: `IcsWriterTest` already pins `DTSTART;TZID=Europe/Moscow:20261025T150000` for 2026-10-25 12:00Z, so it guards against #903 once the patch is gone. No MongoDB schema change and no Mongock migration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The `calendar-export` requirements ("Event time zone": the time zone does not change the instant an entry starts or ends) stay as they are; only the way they are met changes (`skip_specs: true`).

## Impact

- `build.gradle`: ical4j version, jspecify removed.
- Removed: `src/main/java/net/fortuna/ical4j/model/ZoneRulesBuilder.java`, `src/test/java/net/fortuna/ical4j/model/ZoneRulesBuilderPatchTest.java` (and the now empty `net/fortuna/...` package directories).
- Possibly `src/test/resources/calendar/events-moscow.ics` (the `VTIMEZONE` block only).
- Transitive dependencies of the new ical4j release (the runtime classpath must still be free of groovy, caffeine and jparsec, as decided in ics-export).
- Versioning: PATCH bump (dependency upgrade, workaround removed).
