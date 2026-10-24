# Tasks

## 1. Process runs without registered users

- [x] 1.1 Remove the early return on an empty user list in `SgProcessor.process()`. Add an `SgIntegrationTest` case: with no registered users, a fixture with new events saves them to MongoDB (JSON expectation) and `TelegramService.sendPhotoMessage` is never called. Verify: the new test passes, and the existing `SgIntegrationTest` cases still pass.
- [x] 1.2 Add an `SgIntegrationTest` case: events are saved while no user is registered, then a user registers and the same page is processed again. Verify: the test passes, and the user receives no notification for the unchanged events.
- [x] 1.3 Make Telegram optional (design 5a): `BOT_TOKEN`/`BOT_NAME` default to empty, `main()` registers the bot only when a token is set and otherwise logs a warning, the send methods skip sending without a token, and `env_file` in `docker-compose.yml` is optional. Verify: `TelegramServiceTest` (disabled when the token is blank, nothing is executed on send) passes, and the app starts without `.env` (see 5.1).

## 2. Dependency and configuration

- [x] 2.1 Add `org.mnode.ical4j:ical4j:4.4.0` to `build.gradle`. Verify: `./gradlew dependencies --configuration runtimeClasspath` shows ical4j without groovy, caffeine or jparsec, and `./gradlew compileJava` passes.
- [x] 2.2 Create `calendar/IcsExportProperties` (record: `enabled`, `path`, `defaultEventDuration`, `pastPeriod`, `url`) with defaults `enabled=false` and `pastPeriod=3y`. When enabled, the compact constructor fails if `path`, `url` or `defaultEventDuration` is missing. Bind a stopgame instance in a `@Bean` via `Binder.bindOrCreate("site.stopgame.ics", ...)` (records need constructor binding). Add `site.stopgame.ics` to `application.yml`: enabled (the calendar runs by default, the property default stays `false`), `path: ./ics/stopgame.ics`, `default-event-duration: 120m`, `url: https://www.twitch.tv/stopgameru`. Verify: a unit test with an `ApplicationContextRunner` or `Binder` covers the defaults, parsing of `120m` and `3y`, and the failure when export is enabled with a missing field.

## 3. Shared calendar writer

- [x] 3.1 Create the `calendar/CalendarEvent` record (`sourceId`, `start`, nullable `end`, `summary`, `description`, `url`, `imageUrl`, `sequence`, `lastModified`). Verify: `./gradlew compileJava` passes.
- [x] 3.2 Implement `calendar/IcsWriter`. It turns a site key and an ordered list of `CalendarEvent`s into bytes via ical4j `CalendarOutputter`, following design decision 3:
  - fixed `PRODID` (pick the text from the design's Open Questions);
  - each `VEVENT` created with `new VEvent(false)` and its properties added in a fixed order;
  - UID `<sourceId>@<site>.sched-alert-bot`;
  - `DTSTAMP` and `LAST-MODIFIED` from `lastModified`, `SEQUENCE` from `sequence`;
  - UTC `DTSTART`/`DTEND`, `SUMMARY`, `DESCRIPTION`, `URL`, and `IMAGE;VALUE=URI;DISPLAY=BADGE`;
  - empty values omitted.

  Verify: a golden-file test in `src/test/resources/calendar/` passes for an event with Cyrillic text containing `,`, `;`, `\` and newlines, an event without an image, description or end, and an empty calendar. A second test writes twice, more than a second apart, and expects byte-identical output (the writer has no `Clock`; a hidden "now" would come from ical4j's system time). A third test expects different UIDs for the same `sourceId` under two site keys.
- [x] 3.3 Implement `calendar/CalendarExporter`:
  - sets end = start + `defaultEventDuration` when the event has none;
  - drops events whose start is before the cutoff, computed via a UTC `ZonedDateTime` minus `pastPeriod` from the injected `Clock`;
  - sorts by start, then UID;
  - writes atomically: creates the parent directories, writes to a temp file in the same directory, then `Files.move` with `ATOMIC_MOVE` + `REPLACE_EXISTING`, falling back to `REPLACE_EXISTING`; deletes the temp file in `finally`;
  - exposes `fileExists()`.

  Verify: `@TempDir` unit tests cover these cases:
  - the default duration is applied;
  - an event exactly at the cutoff is included and one just before it is excluded, with `pastPeriod=3y`;
  - an event two years ahead is included;
  - the output order does not depend on the input order;
  - missing directories are created;
  - an existing file is replaced, and no `*.tmp` file is left behind.

## 4. Stopgame mapping and export trigger

- [x] 4.1 Implement `site/stopgame/SgCalendarMapper` as a MapStruct mapper (`componentModel = SPRING`), following design decision 2a:
  - `toCalendarEvent(SgEventEntry entry, String url)` maps `id`, `name`, `imageUrl` and `url` directly, and `sequence` from `version` with `defaultValue = "0"`;
  - default methods called from `expression = "java(...)"` compute `start`, `end`, `description` and `lastModified` (not `source = "entry"`, which disables the unmapped-source check, see design 2a):
    - SCHEDULED and LIVE: start = `date`, no end;
    - FINISHED: start = `startedAt ?? date`, end = `endedAt` only if `startedAt` is known;
    - `description` = the description, a blank line, then the participants joined with `, ` (either part may be absent);
    - `lastModified` = `updatedAt ?? createdAt ?? start`;
  - the source properties read only by those methods go into `ignoreUnmappedSourceProperties`;
  - the default method `toCalendarEvents(List<SgEventEntry>, String url)` drops CANCELED entries and entries without a resolvable start.

  Verify:
  - `./gradlew compileJava` passes with the build's `unmapped*Policy=ERROR`; removing a name from the ignore list or a target mapping fails compilation;
  - unit tests on `Mappers.getMapper(SgCalendarMapper.class)` cover each status and each fallback from the `calendar-export` scenarios ("Entry time for scheduled and live events", "Entry time for finished events", "Entry content", "Included events").
- [x] 4.2 Have `process()` collect the decided events, handle them, and check whether any was saved (`shouldSave`). After the pipeline, if export is enabled and (`saved > 0` or the file does not exist), load events with `findAllByStatusInStartingFrom` (`@Query`: statuses SCHEDULED, LIVE, FINISHED and `date` or `startedAt` ≥ `CalendarExporter.cutoff()`), map them with `SgCalendarMapper.toCalendarEvents` and export. Wrap the export in `try/catch (Exception)` and log at ERROR. Verify: `SgIntegrationTest` cases with a temp export path and a `MutableClock`:
  - export disabled (in `SgProcessorTest`, with a real exporter on `@TempDir`, since the integration context has export enabled): no file is created, and an existing file is unchanged;
  - a run with a save writes the file, which contains every included stored event, not only the saved one;
  - a run without saves leaves the file unchanged (same bytes and modification time);
  - the file is written when it is missing even though nothing was saved;
  - a LIVE event processed over several runs keeps the file byte-identical;
  - a LIVE event that disappears (FINISHED) regenerates the file with its real start and end;
  - an event that becomes CANCELED is removed from the file;
  - events older than the past period are neither returned by the query (boundary: started or scheduled exactly at the cutoff is included; CANCELED and undated are excluded) nor written;
  - an exporter that throws (via `@SpyBean`) still saves the event, sends the notification, and the next run with a save writes the file.

## 5. Deployment and docs

- [x] 5.1 Add the `./ics:/app/ics` volume to the app services in `docker-compose.yml`. Verify: `docker compose config` shows the volume. Without `.env` (no Telegram token) and with `SITE_STOPGAME_ICS_ENABLED=true`, `docker compose up app-local-build` starts, logs that Telegram is disabled, and creates `./ics/stopgame.ics` on the host after the first run.
- [x] 5.2 Document the `site.stopgame.ics.*` settings in the README: meaning, defaults, required fields when enabled, the volume, and that canceled events disappear from the file. Verify: the README renders and the documented keys match `application.yml`.
- [x] 5.3 Import a generated file (with `time-zone: Europe/Moscow`, after section 7) into Google Calendar and Apple Calendar. Verify: events appear with the right local times, titles and descriptions, Cyrillic text is intact, and Apple Calendar shows the Moscow time next to the local time. Note the result in the PR body, since the file has lines longer than 75 octets (see design Risks).

## 6. Final check

- [x] 6.1 Run `./gradlew test` (after section 7). Verify: all tests pass, including the existing event lifecycle integration tests (new, changed, live, finished, canceled, returned after cancel).

## 7. Event time zone

- [x] 7.1 Add the optional `timeZone` (`ZoneId`, no default) to `IcsExportProperties`, and `time-zone: Europe/Moscow` to `site.stopgame.ics` in `application.yml`. Verify: `IcsExportPropertiesTest` covers the unset value, binding `Europe/Moscow`, and an unknown id failing to bind.
- [x] 7.2 Pass the time zone from `CalendarExporter` to `IcsWriter` (design 3a). When set, `IcsWriter` adds the ical4j registry's `VTIMEZONE` once before the events and writes `DTSTART`/`DTEND` with `TZID`; `DTSTAMP` and `LAST-MODIFIED` stay in UTC. Verify:
  - the existing golden files pass unchanged with no time zone;
  - a new golden file `events-moscow.ics` shows `DTSTART;TZID=Europe/Moscow:...` with the local time and exactly one `VTIMEZONE`;
  - output is byte-identical across two writes with a time zone set;
  - an empty calendar with a time zone set is valid;
  - an event on 2026-10-25 12:00Z is written as `DTSTART;TZID=Europe/Moscow:20261025T150000` (+04:00 without the patch, ical4j#903);
  - Europe/Amsterdam gives the winter and summer local times.
- [x] 7.4 Patch ical4j's `ZoneRulesBuilder` (design 3a): copy the 4.4.0 source to `src/main/java/net/fortuna/ical4j/model/`, apply the fix from ical4j#903 with a `PATCH` comment and a class javadoc linking the issue, add `compileOnly` jspecify. Verify:
  - `ZoneRulesBuilderPatchTest`: the copy is loaded instead of the jar's class, Europe/Moscow is +03:00 in 2026, and the test fails on an ical4j version other than 4.4.0;
  - `build/libs/app.jar` run with `java -jar` writes Moscow times with +03:00.
- [x] 7.3 Document `time-zone` in the README. Verify: the documented key matches `application.yml`.

## 8. Description from a template

- [x] 8.1 Add `templates/stopgame-calendar.txt.ftl` (design 2b): description, participants joined with `, `, and the hardcoded Twitch, YouTube and StopGame links, as blocks separated by a blank line, empty blocks omitted. Turn `SgCalendarMapper` into an abstract class with an injected `TemplateService`; `calendarDescription` renders the template and trims the result. Verify:
  - `SgCalendarMapperTest`: full description; no participants; no description; neither (links only);
  - the build still fails on an unmapped source or target property;
  - golden and integration tests pass with the new description.
- [x] 8.2 Mention the calendar template in the README. Verify: the README renders.
- [x] 8.3 Rename the Telegram template `stopgame.md.ftl` to `stopgame-telegram.html.ftl` (Telegram messages use `ParseMode.HTML`), next to `stopgame-calendar.txt.ftl`. Verify: `SgTemplateServiceTest` and `SgIntegrationTest` pass.

## Workflow follow-up

- Sync the specs, then make sure the `## Overview` note in `openspec/specs/event-notifications/spec.md` still reads correctly with the new "Recipients" requirement. Transitions and notifications do not change, so the Mermaid diagram stays as is.
- Archive the change before merge.
