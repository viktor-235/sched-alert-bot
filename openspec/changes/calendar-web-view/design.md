# Design

## Context

See proposal.md (Why) for motivation and `specs/calendar-web/spec.md` for the required behavior.

Current state that shapes the approach:

- `viktor-235/sched-alert-calendars` is served by Pages from `main` at the root, with `.nojekyll`. The bot changes only `stopgame/streams.ics` through the Contents API (`calendar-publishing`), so hand-committed pages next to it never conflict with the bot.
- `streams.ics` is served as `text/calendar` with `Access-Control-Allow-Origin: *` and `Cache-Control: max-age=600`. The bot commits at most once per 10 minutes.
- The file uses `TZID=Europe/Moscow` with a full `VTIMEZONE` (since 1880), RFC 7986 `IMAGE;VALUE=URI;DISPLAY=BADGE`, `URL` set to the Twitch channel, and a `DESCRIPTION` whose last block is `Twitch: …`, `YouTube: …`, `StopGame: …` lines (`calendar-export`, "Entry description"). There is no status property. Scheduled and live entries last the default duration (2 h).
- The file keeps finished streams for 3 years, so it grows to a few hundred entries; parsing the whole file in the browser is cheap.

## Goals / Non-Goals

**Goals:**
- Plain files, copyable as is into the calendars repository, runnable locally with `python3 -m http.server -d pages`.
- Correct event times for viewers in any time zone.
- Adding a site later means a new tile, a new folder with its page and string files, and no change to shared code.

**Non-Goals:**
- Supporting old browsers: current evergreen browsers only (ES2020, `<dialog>`, `fetch`, `Intl`).
- Automated tests for the pages: they are checked by hand (tasks.md), the bot's `./gradlew test` is unaffected.

## Decisions

### 1. Development folder `pages/` mirrors the calendars repository

```
pages/
  index.html
  i18n/en.json
  assets/
    site.css            # palette, layout, components shared by all pages
    i18n.js             # loader, language choice, switch
    calendar-page.js    # site page: subscribe, copy, calendar, dialog, refresh
    vendor/
      README.md         # package, version, source URL, license of each file
      fullcalendar/index.global.min.js, LICENSE.txt
      fullcalendar/locales/ru.global.min.js
      ical.js/ical.min.js, LICENSE
  stopgame/
    index.html
    i18n/en.json, i18n/ru.json
    streams.ics         # local copy of the published file, for development only
```

`pages/` is listed in `.gitignore` of this repository. Moving the pages means copying `pages/` into the calendars repository without `stopgame/streams.ics`, which belongs to the bot. Pages reference everything by relative paths (`../assets/…`, `streams.ics`), so the same files work under `/sched-alert-calendars/` and under `localhost`.

The site page is a thin HTML file: markup with `data-i18n` keys plus the calendar file name on `<body data-calendar="streams.ics">`, read by `calendar-page.js`; the Telegram and official schedule links are plain links in the markup. A new site copies the page and changes those.

### 2. FullCalendar v6, vendored, without its iCalendar plugin

FullCalendar 6.1.22 standard bundle (`index.global.min.js`, ~285 KB: day grid, time grid, list, all in one global `FullCalendar`) and its `ru` locale, plus ical.js 1.5.0 (`build/ical.min.js`, global `ICAL`). Files are downloaded once from npm (through jsDelivr) into `assets/vendor/` with their license files and listed in `vendor/README.md`. No CDN at runtime, so no SRI.

The `@fullcalendar/icalendar` plugin is not used. Its v6 source converts times with `startDate.toString()`, which drops `TZID` and turns `15:00 Europe/Moscow` into a floating `15:00` in the viewer's zone; it also sets the entry `url` from the file's `URL`, so a click navigates to Twitch instead of opening the details dialog, and it ignores `IMAGE`. Instead `calendar-page.js` parses the file itself with ical.js (decision 3) and gives FullCalendar a function event source.

Alternative: FullCalendar 7.1.3. Rejected for now: it needs a Temporal polyfill, its browser bundle is about three times larger, and its iCalendar plugin is not needed anyway (decision 3 works with either). Upgrading later changes only the vendored files and the theming CSS.

Alternative: own list and grid without a calendar library. Rejected: month and week grids, overlapping entries, navigation and locale-aware formatting are exactly what FullCalendar already does.

### 3. Parsing the calendar file with ical.js

On each load: `fetch('streams.ics', { cache: 'no-cache' })`, `ICAL.parse`, then:

- register every `VTIMEZONE` of the file with `ICAL.TimezoneService`, so `TZID` references resolve;
- for each `VEVENT`: `start`/`end` as absolute instants (`startDate.toJSDate()`, which converts through the registered zone to UTC), `title` from `SUMMARY`, and `extendedProps` with `description`, `image` (first `IMAGE` value) and `uid`. The file's `URL` is not passed as the entry `url`.

FullCalendar gets `timeZone: 'local'`, so instants are shown in the viewer's zone. The zone name in the caption comes from `Intl.DateTimeFormat().resolvedOptions().timeZone`.

The parsed entries are cached in memory. The event source function returns them for any range, so moving between months does not refetch the file; refresh (decision 6) replaces the cache and calls `refetchEvents()`.

### 4. Description links

The description is split into blocks by blank lines. If the last block consists only of `<name>: <url>` lines with `http(s)` URLs, it becomes the dialog's link buttons and is removed from the shown text; otherwise the whole description is shown as text. Text is inserted with `textContent` (never `innerHTML`), line breaks are kept with `white-space: pre-line`. Buttons open in a new tab with `rel="noopener"`.

### 5. Views, past and live

- Views: `dayGridMonth`, `timeGridWeek` (no all-day row), and a custom list view `listDays` = `{ type: 'list', duration: { days: 14 } }` that starts today. `firstDay: 1`, 24-hour `eventTimeFormat` and `slotLabelFormat`, `nowIndicator: true`, `eventInteractive: true` so entries are focusable and open with Enter.
- Locale: `ru` for Russian and `en-gb` for English. The bundle has no `en-gb` locale data, so FullCalendar falls back to its `en` texts but formats dates through `Intl` with `en-gb`: day-month order and the 24-hour clock.
- Initial view: `matchMedia('(max-width: 640px)')` picks the list view, otherwise the month view. The choice is made once at load; the toolbar switches views afterwards.
- The event source sets `classNames` `is-past` (end ≤ now) or `is-live` (start ≤ now < end) on each entry; `eventDidMount` prepends a pulsing "LIVE" badge to live entries. A one-minute timer calls `calendar.refetchEvents()`, which re-reads the in-memory cache (decision 3), so past and live classes follow the clock without network requests.
- Clicking an entry (`eventClick`) opens a `<dialog>` with `showModal()`; the browser handles Escape and keeps Tab inside it; a click on the backdrop closes it.

### 6. Refresh

A 10-minute interval runs a load only while `document.visibilityState === 'visible'`; a `visibilitychange` to visible runs a load at once when the last load is older than one minute. A failed load with entries already shown is logged to the console only. A failed first load replaces the calendar area with the localized error and a Retry button.

### 7. Localization

`assets/i18n.js`:

- each page declares its supported languages on `<html data-langs="en ru">` and its string folder (`i18n/`);
- choice (spec "Language choice"): `localStorage['lang']` if supported, otherwise the first `navigator.languages` entry whose primary subtag is supported, otherwise `en`. Every `localStorage` access is wrapped in `try/catch`;
- strings: `i18n/<lang>.json`, a flat object `"key": "text"`. Elements with `data-i18n="key"` get `textContent`, `data-i18n-attr="aria-label:key;title:key2"` set attributes, `<title>` and `<meta name="description">` use reserved keys `page.title` and `page.description`. `html[lang]` is set to the language;
- the switch (`EN | RU` buttons in the header) is rendered only when more than one language is supported, saves the choice, re-applies strings and tells the calendar (`calendar.setOption('locale', …)` and the toolbar texts) without reloading;
- `<html>` starts with class `i18n-pending`, which hides `body` content (`visibility: hidden`); it is removed after the strings are applied, or after loading fails (then the English fallback strings already in the markup stay).

FullCalendar texts come from its own `ru` locale (month and day names, date formats, `Today`, view names, "no events" text overridden by our string). Time zone names are never translated.

### 8. Look

CSS custom properties in `site.css`:

| Token | Value | Use |
| --- | --- | --- |
| `--bg` | `#141417` | page background |
| `--surface` | `#1d1d22` | tiles, cards, dialog, calendar |
| `--border` | `#2a2a31` | borders, grid lines |
| `--text` | `#e8e8ec` | main text |
| `--muted` | `#9a9aa5` | secondary text, disclaimer |
| `--surface-raised` | `#25252b` | secondary buttons, list day headers |
| `--accent` | `#d32f2f` | filled buttons and entries (white text 4.98:1) |
| `--accent-hover` | `#c62828` | hover of filled buttons |
| `--accent-text` | `#ff6b63` | links, live marker and focus ring on dark surfaces (6.6:1) |

`color-scheme: dark`, `<meta name="theme-color" content="#141417">`, system font stack, inline SVG favicon (a red calendar glyph) as a `data:` URI. FullCalendar is themed through its `--fc-*` custom properties mapped to these tokens. Text on `--accent` is white. The first draft used `#e53935` as the single accent, but white text on it is 4.23:1, below WCAG AA, so filled surfaces use the darker `#d32f2f` and accent-colored text uses the lighter `#ff6b63`.

## Risks / Trade-offs

- [Long live streams shown as past] The file ends scheduled and live entries after the default duration, so a stream running longer loses its live mark until the bot publishes its real end → documented as a known limitation in the spec; a fix belongs in the export (e.g. an open end for live events), not in the page.
- [Vendored libraries age] No automatic updates → versions and sources are listed in `vendor/README.md`; the libraries run in the visitor's browser on public data only.
- [File growth] Three years of streams is a few hundred entries, well within what ical.js parses instantly → no paging needed.
- [Cache delay] Pages caches the file for 10 minutes on top of the bot's 10-minute interval → a change can take up to ~20 minutes to appear, acceptable for a stream schedule.
- [Event images from a third-party host] Images are loaded from `images.stopgame.ru` only when the dialog opens (the `img` has no `src` until then) → nothing is requested from that host on page load, with `referrerpolicy="no-referrer"`.

## Migration Plan

1. Develop in `pages/` with a copy of the published `streams.ics`.
2. Copy `pages/` without `stopgame/streams.ics` into the calendars repository and commit by hand; Pages publishes it.
3. Rollback: revert that commit in the calendars repository; the bot and the calendar file are unaffected.
