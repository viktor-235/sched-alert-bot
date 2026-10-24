# Proposal

Issue: [#15 Add web calendar view for published schedules](https://github.com/viktor-235/sched-alert-bot/issues/15)

Depends on: `ics-publish` (#14), which publishes `stopgame/streams.ics` to [`viktor-235/sched-alert-calendars`](https://github.com/viktor-235/sched-alert-calendars), served by GitHub Pages at `https://viktor-235.github.io/sched-alert-calendars/`.

## Why

The published calendar is only usable by people who already know its URL and paste it into a calendar app. A small website next to the file lets anyone find the calendar, subscribe in one click, find the Telegram channel, and look at the schedule in the browser without subscribing.

## What Changes

- **Static pages in the calendars repository**, next to the files the bot publishes. Plain HTML, CSS and JavaScript, no build step, no server code. The bot does not write or deploy them; they are committed by hand. Layout:
  ```
  index.html           # home page: one tile per site
  i18n/en.json         # home page strings
  assets/              # shared styles, scripts, vendored libraries
  stopgame/
    index.html         # StopGame page, served at /sched-alert-calendars/stopgame/
    i18n/en.json
    i18n/ru.json
    streams.ics        # written only by the bot (unchanged)
  ```
- **Home page** (English): title, one-line description, a grid of large tiles, one per site (now only StopGame), footer with a link to the bot repository.
- **StopGame page** (English and Russian):
  - intro with what the page is, a link to the [official schedule](https://stopgame.ru/live_schedule) and an "unofficial fan project, not affiliated with StopGame" disclaimer;
  - subscribe block: `webcal://` subscribe link, copy of the `https://` calendar URL to the clipboard, "Add to Google Calendar" link;
  - Telegram block: link to the [@sg_alerts](https://t.me/sg_alerts) channel, where the bot posts stream announcements;
  - interactive calendar rendered from `streams.ics` in the browser: month, week and list views, event details on click, live streams marked, past ones dimmed, periodic refresh.
- **Localization:** each page has its own string files. The home page ships English only; the StopGame page ships English and Russian, chosen by the device language with English as the default, and a manual switch remembered in the browser. Event names and descriptions are shown as they are in the calendar file.
- **Look:** dark theme on a not-quite-black background, red accent.
- **Known limitation:** the calendar file gives scheduled and live streams the default duration (2 hours), so the page stops marking a live stream as live once that time has passed, even if the stream is still running.
- **Affected statuses and users:**
  - Telegram users: no change.
  - Event statuses: no change. The page shows what the published file holds: SCHEDULED, LIVE and FINISHED events, no CANCELED ones. The file carries no status, so the page derives "live" and "past" from the entry times.
  - New audience: visitors of the website.

## Non-goals

- Links to a single event (`#event=…`), filters and search.
- A light theme.
- A Russian version of the home page.
- Deploying the pages by the bot or by CI: they are committed to the calendars repository by hand.

## Capabilities

### New Capabilities
- `calendar-web`: the public website served next to the published calendars: home page with site tiles, per-site page with subscription, Telegram link and an interactive calendar built from the published `.ics` file, localization and look.

### Modified Capabilities
None. The page only reads the file described by `calendar-export` and `calendar-publishing`.

## Impact

- Code of the bot: none. The pages live in the calendars repository; while they are developed they sit in `pages/` of this repository, ignored by git.
- Dependencies: FullCalendar (MIT) and ical.js (MPL-2.0), vendored as files into `assets/vendor/` with their licenses; no CDN at runtime.
- Docs: README links to the website.
- Database, configuration, Telegram templates: no change.
