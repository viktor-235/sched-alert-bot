# Design

## Context

See proposal.md (Why) for motivation and `specs/calendar-export/spec.md` for the required behavior.

Current state that shapes the approach:

- `SgProcessor.process()` returns early when `TelegramService.getUsers()` is empty, then runs a per-event pipeline (`normalize → compare → updateStatus → decide → handle`). `handle` saves the event and sends notifications; it does not report back whether anything was saved.
- `SgEventEntry` already has everything the calendar needs: `id`, `status`, `name`, `date`, `description`, `participants`, `imageUrl`, `startedAt`, `endedAt`, plus `updatedAt` (`@LastModifiedDate`, auditing is enabled in `MongoConfig`) and `version` (`@Version`). No schema change is needed.
- `SgEventRepository.findAllByStatusIn(...)` exists and can load the calendar's events.
- Shared services live in top-level packages (`compare/`, `template/`, `telegram/`, `web/`); sources live in `site/<source>/`. Configuration is read with `@Value`; there are no `@ConfigurationProperties` classes and no bean validation starter yet.
- The follow-up change `ics-publish` uploads the written file only when its bytes change, so the output has to be deterministic.
- Times must come from the injected `Clock`.

## Goals / Non-Goals

**Goals:**
- A shared `calendar/` package that a new source can reuse by writing only a mapper from its stored event to a common calendar event.
- Deterministic output: the same stored events always produce the same bytes.
- Export failures never affect saving or notifications.

**Non-Goals:**
- Publishing the file (`ics-publish`) or serving it over HTTP from the bot.
- Full iCalendar coverage: no recurrence, alarms, attendees or organizer. Time zones are limited to one configured zone per file (decision 3a).
- Incremental updates of the file; it is always regenerated in full.
- Removing users' need to register for notifications, or subscription per site (the existing `todo`).

## Decisions

### 1. ical4j for building and writing the calendar

Add `org.mnode.ical4j:ical4j:4.4.0` (Java 11+). The code builds a `Calendar` with `VEvent`s and writes it with `CalendarOutputter`. ical4j handles the RFC 5545 details that are easy to get wrong:
- CRLF line endings and line folding;
- TEXT escaping (`\\`, `\;`, `\,`, `\n`);
- parameter syntax and date-time formats (UTC and local times with `TZID`);
- bundled `VTIMEZONE` definitions;
- the RFC 7986 `IMAGE` property;
- validation on output, which catches a missing required property in tests.

Cost is moderate:
- The required runtime dependencies are `slf4j-api`, `commons-lang3`, `commons-codec` and `threeten-extra`. The first two are already on the classpath, and caffeine, jparsec and groovy are optional and not pulled in.
- The jar is about 1.7 MB.

A trial run with Cyrillic text, our properties and fixed timestamps produced correct, deterministic output (see decision 3 for how).

Alternative: a hand-written writer (~150 lines plus tests). Its only real advantages are folding at 75 octets instead of 73 chars and full control over the bytes. Neither justifies owning escaping, folding and date formatting ourselves.

Alternative: biweekly. Less maintained (last release 0.6.8 in January 2024), pulls in Jackson and vinnie, and writes its own versioned `PRODID` by default.

### 2. Package layout and responsibilities

```
calendar/
  CalendarEvent          record: sourceId, start, end (nullable), summary, description, url, imageUrl, sequence, lastModified
  IcsExportProperties    record: enabled, path, defaultEventDuration, pastPeriod, url, timeZone
  IcsWriter              CalendarEvent list → ical4j Calendar → bytes (pure, no I/O); the only class that uses ical4j
  CalendarExporter       filter + sort + render + atomic file write; fileExists()
site/stopgame/
  SgCalendarMapper       MapStruct: SgEventEntry → CalendarEvent (status-specific time rules), see decision 2a
  SgProcessor            triggers the export
```

Split of responsibilities:
- **The source mapper resolves times**, because the rules depend on the source's statuses: SCHEDULED/LIVE → start = `date`, end = none; FINISHED → start = `startedAt ?? date`, end = `endedAt` only if `startedAt` is also known; CANCELED or no resolvable start → empty. Content: `summary` = name; `description` is rendered from the site's calendar template (decision 2b); `url` = configured stream URL.
- **The shared code applies generic rules**: end = start + `defaultEventDuration` when the mapper gave no end; drop events whose start is before `now − pastPeriod`; sort; render; write.

Alternative: put the status rules in shared code. Rejected: `EventStatus` belongs to the stopgame model, and other sources may have different statuses.

### 2a. MapStruct for the source mapper

`SgCalendarMapper` is a MapStruct abstract class (`componentModel = SPRING`), like the existing `SgMapper` but a class, so that it can have the `TemplateService` injected for the description (decision 2b). MapStruct 1.6 maps into the `CalendarEvent` record through its canonical constructor.

The build sets `unmappedTargetPolicy=ERROR` and `unmappedSourcePolicy=ERROR`, which gives two checks:
- a new `CalendarEvent` field fails compilation until every source maps it;
- a new `SgEventEntry` field fails compilation until it is mapped or listed in `@BeanMapping(ignoreUnmappedSourceProperties = ...)`.

For the second check to work, no mapping may take the whole entry as its source (`source = "entry"`). MapStruct then deliberately skips the unmapped-source check for all of that parameter's properties (`BeanMappingMethod`: "If the source parameter was directly mapped, ignore all of its source properties completely"). Derived fields therefore use `expression = "java(calendarStart(entry))"`. Such expressions have no source reference, and the generated code still compiles against the interface's default methods.

Shape:
- **Direct mappings:** `CalendarEvent toCalendarEvent(SgEventEntry entry, String url)` maps `sourceId ← id`, `summary ← name`, `imageUrl ← imageUrl` and `url ← url`. The URL comes from `IcsExportProperties` and is passed as a second source parameter.
- **Derived fields:** `start`, `end`, `description` and `lastModified` use `expression = "java(...)"` calls to default methods (`calendarStart`, `calendarEnd`, `calendarDescription`, `calendarLastModified`; methods of the abstract class) that hold the status rules from decision 2. The source properties they read (`status`, `date`, `description`, `participants`, `startedAt`, `endedAt`, `createdAt`, `updatedAt`) are listed in `ignoreUnmappedSourceProperties`.
- **Sequence:** `sequence ← version` with `defaultValue = "0"`.
- **Filtering:** a default method `List<CalendarEvent> toCalendarEvents(List<SgEventEntry> entries, String url)` drops CANCELED entries and entries whose `calendarStart` is null, then maps the rest. MapStruct has no notion of "no result", so filtering stays in plain Java in the same interface.

Alternative: `source = "entry"` with `@Named` methods. It reads better than string expressions, but silently disables the unmapped-source check (see above).

Alternative: a hand-written `@Component` mapper. It is equally short, but loses both compile-time checks, and it would be the only hand-written mapper in a project that otherwise uses MapStruct.

### 2b. Description from a template

The description is rendered by FreeMarker through the existing `TemplateService`, like the Telegram messages: `templates/stopgame-calendar.txt.ftl`, with `description` and `participants` in the context. The template holds the whole text, including the stream links, which are hardcoded in it:

```
<description>

<participants joined with ", ">

Twitch: https://www.twitch.tv/stopgameru
YouTube: https://www.youtube.com/@StopgameRuOnline/live
StopGame: https://stopgame.ru/live_schedule
```

- Why links in the description: Google Calendar ignores `URL` on import, and no client shows `IMAGE`; a link in plain text is made clickable by every client. Apple Calendar shows the Twitch link twice (`URL` and description), which is acceptable.
- Link names are not domain names: Apple Calendar turns `StopGame.ru` into a link of its own, so the name is `StopGame`.
- `@StopgameRuOnline/live` opens the channel's current or next live stream. The site has no per-stream links.
- The output is plain text (`.ftl` has no auto-escaping) and is trimmed; ical4j escapes it for iCalendar.
- `SgCalendarMapper` gets `TemplateService` by field injection, the only option for a MapStruct abstract class. Unit tests set it with a real FreeMarker configuration.
- A template change rewrites every entry on the next run that saves something; `SEQUENCE` does not change, which clients accept for a description change.

Alternative: named links (`<a href>`) in `DESCRIPTION` or an HTML `X-ALT-DESC`. Rejected: Apple shows the tags as text, and only Outlook reads `X-ALT-DESC`.

Alternative: a list of links in `application.yml`. Rejected: more configuration and binding code for text that changes as rarely as the template.

### 3. Deterministic content

- `UID` = `<sourceId>@<site>.sched-alert-bot`, e.g. `12345@stopgame.sched-alert-bot`. The site key is passed to the exporter by the source, so UIDs from different sites never collide.
- `SEQUENCE` = `version` (`0` if null); `DTSTAMP` and `LAST-MODIFIED` = `updatedAt`, falling back to `createdAt`, then to the event start, so they never depend on the current time. Using the last update time for `DTSTAMP` is a deliberate reading of RFC 5545: for a published calendar without a method it is the time the information was last revised.
- `PRODID` is set explicitly to a fixed string without the application version, so a release alone does not change the file.
- Events are created with `new VEvent(false)`. The no-argument constructor adds a `DTSTAMP` with the current time, which would change the file on every run.
- ical4j writes properties in the order they are added, so `IcsWriter` adds them in a fixed order.
- `VEVENT`s are sorted by start, then UID. MongoDB does not guarantee return order.
- Start and end are written per decision 3a. `DTSTAMP` and `LAST-MODIFIED` are passed as `Instant`, so they are always in UTC (`...Z`).
- `IMAGE;VALUE=URI;DISPLAY=BADGE:<imageUrl>` (RFC 7986). Empty values are omitted.

### 3a. Time zone of event times

- `IcsExportProperties.timeZone` (`ZoneId`) is optional and has no default. Unset: start and end are passed as `Instant`, ical4j writes them in UTC (`...Z`), and there is no `VTIMEZONE`. The output is the same as without this setting, so the existing golden files stay valid.
- Set: `IcsWriter` takes the `VTimeZone` for the id from ical4j's `TimeZoneRegistry`, which loads the tzurl.org definition bundled in the jar (`zoneinfo/<id>.ics`), and adds it once to the calendar before the events. Start and end are passed as `ZonedDateTime` in the registry's zone, so ical4j writes `DTSTART;TZID=<id>:<local time>`.
- stopgame sets `time-zone: Europe/Moscow` in `application.yml`: the site announces times in Moscow time.
- Why: Apple Calendar shows the event's own zone next to the local time, e.g. `13:00 (15:00 GMT+3)`, so viewers also see the time as announced. Other clients convert to their own zone as before.
- Determinism: ical4j's online refresh of definitions (`net.fortuna.ical4j.timezone.update.enabled`) is off by default, so the block comes only from the jar and changes only with an ical4j upgrade. The block is the full historical definition (Europe/Moscow: about 3 KB); it is not trimmed, since that would mean generating our own `VTIMEZONE`.
- An unknown zone id fails binding at startup. The past-period cutoff stays computed in UTC; the zone affects only rendering.

- ical4j bug [#903](https://github.com/ical4j/ical4j/issues/903): when writing, ical4j derives the local time from its own `ZoneRules`, built from the bundled definition by `ZoneRulesBuilder`. An observance without an `RRULE` blocks its "every `RRULE` expired" check, so zones that abolished DST keep a yearly DST rule: Europe/Moscow is at +04:00 from every last Sunday of March. Across 2015-2030, 69 of 339 bundled zones disagree with the JDK. The issue was closed together with PR #904, which fixes a different bug (#902); the line is unchanged in 4.4.0 and on `develop`.
- Workaround: `src/main/java/net/fortuna/ical4j/model/ZoneRulesBuilder.java` is a copy of the 4.4.0 class with the one-line fix from the issue (marked `PATCH`), shadowing the class in the jar. Application classes come first on the classpath in Gradle and in the Spring Boot jar. With the fix, 5 zones still disagree with the JDK in future years (Africa/Cairo, Africa/Casablanca, Africa/El_Aaiun, Asia/Gaza, Asia/Hebron), the same 5 as without it; none gets worse. `compileOnly` jspecify keeps the copy identical to the original apart from the fix and its javadoc.
- `ZoneRulesBuilderPatchTest` checks that the copy is loaded instead of the jar's class, that Moscow is at +03:00 in 2026, and fails on any ical4j version other than 4.4.0, so an upgrade forces a decision: delete the copy if the release fixes #903, otherwise re-apply the fix to the new source.

Alternative: build the `VTIMEZONE` from JDK `ZoneRules` and register it in an ical4j registry used for `DTSTART`/`DTEND`. Tried and working, but about 70 lines of our own iCalendar time zone code that stay after ical4j is fixed; the patch is one line and is removed by deleting a file.

Alternative: `net.fortuna.ical4j.timezone.update.enabled=true`. Rejected: tzurl.org serves the same definition, the bug is in deriving rules from it, and it adds a network call.

Alternative: `TZID` without a `VTIMEZONE`. Rejected: RFC 5545 requires a `VTIMEZONE` for every referenced `TZID`.

### 4. Configuration binding

`IcsExportProperties` is a shared record. Each source binds its own instance in a `@Bean` with `Binder.bindOrCreate("site.stopgame.ics", IcsExportProperties.class)`, so the prefix stays per site while the type is shared. `@Bean @ConfigurationProperties` is not used, because it only supports JavaBean binding, not a record's constructor binding. Defaults come from `@DefaultValue` on the record components, so a missing section binds to a disabled export.
- Defaults: `enabled = false`, `pastPeriod = 3y`, `timeZone` unset (UTC).
- When `enabled` is true, `path`, `url` and `defaultEventDuration` are required. The record's compact constructor checks them, so the application fails at startup with a clear message instead of on the first export. There is no bean validation starter, and one check does not justify adding it.
- Spring Boot converts `120m` to `Duration` and `3y` to `Period`.
- Pitfall: `Instant.minus(Period.ofYears(3))` throws. The cutoff is computed as `ZonedDateTime.now(clock).withZoneSameInstant(UTC).minus(pastPeriod).toInstant()`.

### 5. When and how the processor exports

- Remove the early return on no users. `sendTgMsg` already loops over `getUsers()`, so with no users it sends nothing. Nothing else depends on the early return.
- `handle` reports whether it saved. `process()` collects the pipeline into a list and counts saves (instead of `forEach`).
- After the pipeline, if export is enabled and (`saved` or the file does not exist), call the exporter. Events are loaded with `findAllByStatusInStartingFrom(statuses, cutoff)`, an `@Query` of `status ∈ statuses AND (date ≥ cutoff OR startedAt ≥ cutoff)`, with statuses SCHEDULED, LIVE and FINISHED and `cutoff = CalendarExporter.cutoff()`. The query is a coarse pre-filter: the resolved start is either `startedAt` or `date`, so every event that passes the exact filter in the exporter also passes the query. The exporter then maps, filters exactly and writes. Without the pre-filter the export would load the whole history, which grows without bound over the years.
- The whole export call is wrapped in `try/catch (Exception)` and logged at ERROR. It runs after all saves and notifications of the run, so a failure cannot affect them.

Alternative: export from a separate scheduler on its own cron. Rejected: it would either rewrite the file without changes or need its own change detection, and the "after a save" trigger is exactly what the spec asks for.

Alternative: load all events and filter only in memory. Rejected: memory use grows with the whole history. Alternative: a query derived from the method name. Rejected: `Or` needs the statuses repeated on both sides (`findAllByStatusInAndDateGreaterThanEqualOrStatusInAndStartedAtGreaterThanEqual`), which is unreadable. Alternative: an exact query on the resolved start. Rejected: a pre-filter is enough, and the exact rule stays in one place, the exporter.

### 5a. Telegram is optional

The telegrambots starter's auto-configuration is not active: it is declared in `spring.factories`, which Spring Boot 3 no longer reads for auto-configuration. The bot is registered by hand in `SchedAlertBotApplication.main()` after the context starts, and `telegram.bot.token` had no default, so the application did not start without `BOT_TOKEN`.

- `telegram.bot.token` and `telegram.bot.username` default to empty.
- `AbstractTelegramService.isEnabled()` is true when the token is not blank.
- `main()` registers the bot only when it is enabled; otherwise it logs a warning and returns. The scheduler keeps the application running.
- `sendMessage` and `sendPhotoMessage` return early when Telegram is disabled, logging at DEBUG. Registered users in the database do not cause sending errors on every run.
- In `docker-compose.yml`, `env_file` is `required: false`, so `docker compose up` works without `.env`.

Alternative: make `TelegramService` a conditional bean and hide it behind a notifier interface. That is cleaner, but `SgProcessor` and the commands depend on the concrete service; a token check is enough for now.

### 6. Atomic file replacement

The writer creates the parent directories, writes UTF-8 bytes to a temp file in the same directory (`Files.createTempFile(dir, <name>, ".tmp")`), then calls `Files.move(tmp, path, ATOMIC_MOVE, REPLACE_EXISTING)`. If the file system rejects `ATOMIC_MOVE` (`AtomicMoveNotSupportedException`), the move falls back to `REPLACE_EXISTING` only. The temp file is deleted in `finally` if the move did not happen. On POSIX file systems the temp file gets `rw-r--r--` before the move: `Files.createTempFile` creates owner-only files, and the move would carry that over to the calendar, which other processes (a web server, the publisher) need to read. Using the same directory keeps the temp file on the same file system, which matters for the Docker bind mount.

### 7. Deployment

`docker-compose.yml` mounts `./ics:/app/ics` for the app services, next to `./logs:/app/logs`. The default `path: ./ics/stopgame.ics` resolves inside it. The README documents the `site.stopgame.ics.*` settings.

### 8. Tests

- `IcsWriter`: a golden-file test of our mapping onto ical4j (property set and order, `PRODID`, no generated `DTSTAMP`, UTC, `IMAGE`, empty values omitted, escaped Cyrillic text), a second golden file with a configured time zone (`TZID`, one `VTIMEZONE`, UTC `DTSTAMP`), plus byte-identical output for equal input in both variants. ical4j's own folding and escaping are not re-tested beyond what the golden file shows.
- `SgCalendarMapper`: unit tests on the generated implementation (`Mappers.getMapper`) for each status and time fallback, and for the filtering in `toCalendarEvents`.
- `CalendarExporter`: past-period cutoff (including a `Period` in years), sorting, directory creation, replacing an existing file, and a valid empty calendar when no event is included. Uses `@TempDir`.
- `SgIntegrationTest` (HTML fixtures, fixed `Clock`):
  - export disabled writes nothing;
  - written after a save and when missing, untouched otherwise;
  - a LIVE event across runs keeps the file identical;
  - an exporter failure still saves and notifies;
  - with no users, events are saved and nothing is sent.

## Risks / Trade-offs

- [Runs with no users now save events, so a user who registers later is not told about events stored before] → Intended and specified in `event-notifications`. The bot has users in production, so in practice this only affects fresh installs.
- [ical4j folds lines at 73 chars, not 75 octets, so Cyrillic lines reach about 150 bytes] → RFC 5545 says lines SHOULD NOT exceed 75 octets, which is a recommendation, and common clients accept longer lines. Before enabling the export in production, import the file manually into Google Calendar and Apple Calendar. If a client ever rejects it, the bytes from `CalendarOutputter` can be re-folded by octets in `IcsWriter` without changing anything else.
- [The patched `ZoneRulesBuilder` copy silently replaces the class of a newer ical4j] → `ZoneRulesBuilderPatchTest` fails on any version other than 4.4.0, see decision 3a.
- [An ical4j upgrade can change the output format or the bundled `VTIMEZONE` definitions] → The golden-file test fails on upgrade, so the change is seen and accepted on purpose. In production it only rewrites the file once, which means one extra commit for `ics-publish`.
- [ical4j's defaults leak non-determinism, e.g. a `VEvent` created with the wrong constructor gets a `DTSTAMP` of now] → Only `IcsWriter` touches ical4j. The golden file pins `DTSTAMP`, and the byte-identical test writes twice more than a second apart and expects the same output.
- [`DTSTAMP` = `updatedAt` instead of the generation time is unusual] → Clients use it only to order revisions, and `SEQUENCE` and `LAST-MODIFIED` change together with it. Byte-stable output is worth more for `ics-publish`.
- [Changing `time-zone` (or `url`, `default-event-duration`) rewrites the file only on the next run that saves something] → Accepted; delete the file to force a rewrite on the next run.
- [A failed write leaves an outdated file until the next run that saves something] → Accepted by the spec. Runs save at least whenever a stream starts or ends. The error is logged.
- [A crash between writing the temp file and the move leaves a `*.tmp` file next to the calendar] → Harmless, and the `finally` cleanup covers non-crash failures. `ics-publish` publishes only the configured path.

## Migration Plan

1. The property default is disabled, but the bundled `application.yml` enables the stopgame export, so the calendar is written right after deploy. Add the `./ics` volume before deploying; the first run writes the file because it is missing.
2. To deploy without the export, set `SITE_STOPGAME_ICS_ENABLED=false`. Runs with no users still save events.
3. Rollback: set `enabled=false` (the file stays but is no longer updated; delete it by hand if needed), or redeploy the previous image. No data migration is involved in either direction.

## Open Questions

- Exact `PRODID` text, e.g. `-//viktor-235//sched-alert-bot//EN`. It is fixed once chosen and does not affect behavior.
