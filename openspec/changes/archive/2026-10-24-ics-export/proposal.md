# Proposal

Issue: [#13 Add optional iCalendar (.ics) export for sites](https://github.com/viktor-235/sched-alert-bot/issues/13)

## Why

The schedule is only available as Telegram messages, so it cannot be seen in a calendar app or subscribed to. An `.ics` file with the site's current schedule is the first step; publishing it (e.g. via GitHub Pages) comes in separate issues.

## What Changes

- New optional, per-site iCalendar export, disabled by default. Configured per site, e.g. for stopgame:

  ```yaml
  site:
    stopgame:
      ics:
        enabled: false                 # default
        path: ./ics/stopgame.ics
        default-event-duration: 120m   # when the real duration is unknown
        past-period: 3y                # default; how far back events are kept
        url: https://www.twitch.tv/stopgameru
        time-zone: Europe/Moscow       # optional; UTC when not set
  ```

- **Processing no longer checks for Telegram users.** Every run scrapes, compares and saves as usual. With no registered users, events are saved and no message is sent. A user who registers later is not notified about events already stored.
- **Telegram is optional.** Without `BOT_TOKEN` the application starts, scrapes, saves and exports as usual; the bot is not registered and nothing is sent. This lets the calendar run without a bot, e.g. locally or in a calendar-only deployment.
- **When the file is written:** after a run that saved at least one event (including a stream that ended), or when the file does not exist yet. A run without saves leaves an existing file untouched. The file is the site's current schedule snapshot and replaces the previous one. Parent directories are created as needed.
- **Which events:** stored SCHEDULED, LIVE and FINISHED events whose start is not earlier than `now − past-period` (default 3 years), with no upper bound. CANCELED events are never in the file. Events without a start (see below) are skipped.
- **One `VEVENT` per included event:**
  - UID derived from the site and the source event id, so it is stable between runs; a changed event updates its existing calendar entry instead of creating a new one.
  - `SEQUENCE` from the stored event's `version`, `LAST-MODIFIED` and `DTSTAMP` from its `updatedAt`. Clients that imported the file once accept updates. A regenerated file with no changed events is byte-identical, which keeps diffs empty once the file is published from git.
  - Time depends on the status, so the file does not change while a stream is running:
    - SCHEDULED and LIVE: start = scheduled `date`, duration = `default-event-duration`. `startedAt` of a LIVE event is not used.
    - FINISHED: start = `startedAt` (the stream really started), end = `endedAt`. If `startedAt` is unknown, start = `date`; if either is unknown, duration = `default-event-duration`.
    - An event whose start resolves to nothing (no `date`, and for FINISHED also no `startedAt`) is skipped.
  - Times are written in the site's configured time zone with `TZID` and a `VTIMEZONE` block (stopgame: `Europe/Moscow`, the time the site announces); without one, in UTC (`...Z`). Each calendar client renders them in its own time zone; Apple Calendar also shows the original zone's time.
  - `SUMMARY` = name; `DESCRIPTION` = description, participants and links to Twitch, YouTube and StopGame, rendered from a FreeMarker template like the Telegram messages (Google Calendar ignores `URL`); `URL` = the site's configured `https` stream link (`https://www.twitch.tv/stopgameru` for stopgame, which also opens the Twitch app on phones); the event image is attached.
- **Reusable writer:** the calendar model and writer are shared, outside `site/`. A source only maps its event model to the common calendar event; stopgame is the first one (`SgEventEntry` → calendar event).
- **Failure isolation:** a failure to build or write the file is logged and does not stop saving events or sending Telegram notifications.
- **Deployment:** `docker-compose.yml` gets a volume for the `ics/` directory.
- **Affected statuses and users:**
  - Telegram users: no change in which notifications they get or how they look. Runs with no registered users now save events.
  - The export includes SCHEDULED, LIVE and FINISHED events. A canceled event disappears from the file. Subscribed calendars remove it on refresh. A calendar that imported the file once keeps it.

## Assumptions

- Stored events are the source of the calendar, not the scraped page: the page only shows upcoming events, while the calendar also keeps past ones.
- "Valid `.ics`" means RFC 5545 output that common clients (Google Calendar, Apple Calendar) accept; tests check the generated content, not client behavior.
- The image is attached as an RFC 7986 `IMAGE` property with the image URL. Clients that do not support it ignore it.
- The file is replaced atomically (write to a temporary file, then move), so a reader never sees a half-written calendar.
- A failed write is retried by the next run that saves something. A missing file is written by the next run.
- Durations and periods use Spring Boot's formats (`120m`, `3y`).
- No MongoDB schema change.

## Capabilities

### New Capabilities
- `calendar-export`: per-site iCalendar export of stored events: configuration and defaults, when the file is written, which events are included, how they map to `VEVENT`s (UID, times in UTC or a configured time zone, duration, content), and failure isolation from the rest of processing.

### Modified Capabilities
- `event-notifications`: the "Recipients" requirement changes. A run with no registered users no longer skips processing; it saves events and sends nothing. New requirement "Telegram is optional": without a bot token the application runs and sends nothing.

## Impact

- Code:
  - a new shared package for the calendar model and writer;
  - a stopgame mapper from `SgEventEntry` to the calendar event;
  - `SgProcessor`: remove the early return when there are no users, and export after a run that saved something or when the file is missing.
- Config: new `site.stopgame.ics.*` properties in `application.yml`; export disabled by default.
- Dependencies: likely an iCalendar library (e.g. ical4j) or a small hand-written writer; decided in design.
- Tests:
  - calendar generation: UIDs, times in UTC and in a configured time zone, time rules per status (scheduled and live use `date` + default duration; finished uses `startedAt`..`endedAt` with fallbacks), a live event produces the same file on every run, skipped undated and too-old events;
  - disabled configuration writes nothing;
  - file replacement, directory creation, file written when missing;
  - a write failure does not stop notifications;
  - a run with no users saves events and sends nothing.
- Deployment: a volume for `ics/` in `docker-compose.yml`; the README documents the export settings.
- Database: no schema change.
