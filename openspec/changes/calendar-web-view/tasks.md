# Tasks

Pages are checked by hand in a current Chrome and Firefox (desktop and a 375 px wide viewport) served with `python3 -m http.server -d pages`, unless a task says otherwise.

## 1. Setup

- [x] 1.1 Add `pages/` to `.gitignore` and create the folder layout of design decision 1. Verify: `git status` does not list `pages/`.
- [x] 1.2 Vendor FullCalendar 6.1.22 (`index.global.min.js`, `locales/ru.global.min.js`, license) and ical.js 1.5.0 (`build/ical.min.js`, license) into `pages/assets/vendor/`, with `vendor/README.md` listing package, version, source URL and license of each file. Verify: `FullCalendar` and `ICAL` are defined in the browser console on a page loading them.
- [x] 1.3 Download the published `stopgame/streams.ics` into `pages/stopgame/streams.ics`. Verify: the file starts with `BEGIN:VCALENDAR`.

## 2. Shared look and localization

- [x] 2.1 Write `assets/site.css`: palette tokens, layout, header, tiles, cards, buttons, toast, dialog, focus ring, FullCalendar `--fc-*` mapping (design decision 8). Verify: no horizontal scroll at 375 px; every text/background pair meets WCAG AA in a contrast checker, tokens adjusted where not.
- [x] 2.2 Write `assets/i18n.js` (design decision 7): language choice, string loading, `data-i18n` and `data-i18n-attr`, `lang`, title and description, `i18n-pending`, language switch shown only for more than one language, choice saved with `try/catch` around storage. Verify the spec scenarios "Russian device", "Unsupported language", "Switch to English" and "No flash of English" by setting the browser language and by blocking site data.

## 3. Home page

- [x] 3.1 Write `pages/index.html` and `pages/i18n/en.json`: title, description, StopGame tile linking to `stopgame/`, footer link to the bot repository, favicon, `theme-color`, `color-scheme`. Verify spec "Home page lists the sites" and scenario "Home page" (English with a Russian browser, no language switch).

## 4. StopGame page

- [x] 4.1 Write `pages/stopgame/index.html`, `i18n/en.json` and `i18n/ru.json`: header with back link and language switch, introduction with the official schedule link and disclaimer, subscribe and Telegram blocks, calendar area, time zone caption. Verify spec "Site page introduction" and "Telegram block" in both languages.
- [x] 4.2 Subscribe and copy in `assets/calendar-page.js`: URLs derived from the page address, Google Calendar link, copy with confirmation and select fallback. Verify scenarios "Subscribe link", "Page served elsewhere", "Copied" and "Clipboard unavailable" (deny clipboard permission in the browser).
- [x] 4.3 Calendar loading with ical.js and a FullCalendar function source (design decisions 3 and 5): views, list of 14 days, initial view by width, Monday start, 24-hour times, locale, time zone caption. Verify scenarios "Entries from the file", "Phone", "Desktop" and "Viewer in Berlin" (with the browser or OS zone set to `Europe/Berlin` and to `Europe/Moscow`, the same stream is shown 2 hours apart in winter).
- [x] 4.4 Past and live marking with the one-minute re-evaluation. Verify scenarios "Live stream" and "Finished stream" by editing a copy of `streams.ics` so one entry spans now and one ended an hour ago.
- [x] 4.5 Event details dialog (design decision 4). Verify scenarios "Open details", "Missing parts" (an edited entry without `IMAGE` and `DESCRIPTION`) and "Keyboard use"; the description text with HTML-like content is shown as text.
- [x] 4.6 Refresh, load error and empty state (design decision 6). Verify scenarios "Schedule changed" (edit the local file, switch tabs away and back), "Refresh fails" and "First load fails" (stop the server or rename the file), "Empty list".
- [x] 4.7 Third-party requests. Verify scenario "Libraries load from the website" in the browser network panel: only event images go to another host, and only after a dialog opens.
- [x] 4.8 "Not working?" help in the subscribe block, English and Russian, the same on every platform. Verify spec "Subscription help": the section is collapsed by default, opens with a click and with Enter, its link opens Google Calendar "From URL", no horizontal scroll at 375 px.

## 5. Docs

- [x] 5.1 README: link to the website `https://viktor-235.github.io/sched-alert-calendars/` in the calendar publishing section. Verify: the links open once the pages are published.

## 6. Verification

- [x] 6.1 Run `./gradlew test`. Verify: all tests pass.

## Workflow follow-up

- Copy `pages/` without `stopgame/streams.ics` into `viktor-235/sched-alert-calendars` and commit it there by hand; check the published pages under `https://viktor-235.github.io/sched-alert-calendars/`.
- Archive the change after review.
