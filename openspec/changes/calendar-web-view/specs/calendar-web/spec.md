# Spec Delta

## Purpose

A public website served by GitHub Pages next to the published calendars, so that people can find a site's calendar, subscribe to it, find its Telegram channel and browse the schedule without a calendar app.

## ADDED Requirements

### Requirement: Static site next to the calendars
The website SHALL consist of static files in the calendars repository, served by GitHub Pages together with the published calendar files, and SHALL need no build step and no server code. Each site SHALL have its page in the site's folder, next to its calendar file. The website SHALL NOT change any calendar file.

#### Scenario: Site page URL
- **WHEN** a visitor opens `https://viktor-235.github.io/sched-alert-calendars/stopgame/`
- **THEN** the StopGame page is shown, and the calendar it uses is `stopgame/streams.ics` of the same repository

### Requirement: No third-party scripts or styles
The pages SHALL load all scripts, styles and fonts from the website itself; libraries SHALL be shipped as files of the website with their licenses. Event images, at the URLs given in the calendar file, SHALL be the only resources loaded from other hosts.

#### Scenario: Libraries load from the website
- **WHEN** a visitor opens any page
- **THEN** no script, stylesheet or font is requested from another host

### Requirement: Home page lists the sites
The home page SHALL show a title, a one-line description and one large tile per site with a published calendar. A tile SHALL show the site name and what the calendar holds, and SHALL link to the site's page. The home page SHALL have a footer linking to the bot repository `https://github.com/viktor-235/sched-alert-bot`.

#### Scenario: StopGame tile
- **WHEN** a visitor opens the home page
- **THEN** a StopGame tile is shown, and clicking anywhere on it opens the StopGame page

### Requirement: Site page introduction
The site page SHALL start with a header holding a link back to the home page and the page title, followed by an introduction saying what the calendar and the notifications are, with a link to the site's official schedule (for StopGame `https://stopgame.ru/live_schedule`, opened in a new tab). The introduction SHALL state that the project is unofficial and not affiliated with the site.

#### Scenario: Disclaimer shown
- **WHEN** a visitor opens the StopGame page in any language
- **THEN** the introduction links to the official schedule and says the project is an unofficial fan project, not affiliated with StopGame

### Requirement: Subscribe block
The site page SHALL have a subscribe block, after the introduction, with: the `https://` URL of the calendar file in a read-only field, a copy button, a subscribe link to the same URL with the `webcal://` scheme, an "Add to Google Calendar" link `https://calendar.google.com/calendar/r?cid=<webcal URL>` and a hint that subscribed calendars update automatically. URLs SHALL be derived from the page's own address.

#### Scenario: Subscribe link
- **WHEN** a visitor on `https://viktor-235.github.io/sched-alert-calendars/stopgame/` clicks Subscribe
- **THEN** the browser opens `webcal://viktor-235.github.io/sched-alert-calendars/stopgame/streams.ics`

#### Scenario: Page served elsewhere
- **WHEN** the page is served from `http://localhost:8000/stopgame/`
- **THEN** the field shows `http://localhost:8000/stopgame/streams.ics` and the subscribe link uses `webcal://localhost:8000/stopgame/streams.ics`

### Requirement: Subscription help
The subscribe block SHALL have a collapsed "Not working?" section, the same on every platform, explaining how to add the copied URL by hand in Google Calendar on the web through "From URL", in Apple Calendar on Mac and iPhone, and in Outlook. For Google it SHALL also say that the calendar then syncs to the phone app, where sync may have to be turned on, and that Google refreshes such calendars rarely.

#### Scenario: Google Calendar on a phone
- **WHEN** a visitor reads the Google Calendar help
- **THEN** it says that on a phone links to Google Calendar open the Calendar app, which cannot add a calendar by URL, so the visitor types `calendar.google.com` in the browser's address bar and turns on the desktop site

#### Scenario: Android visitor
- **WHEN** a visitor on Android taps Subscribe or "Add to Google Calendar" and no calendar is added
- **THEN** the "Not working?" section, shown to every visitor, tells them to copy the link and add it in Google Calendar on the web through "From URL", typing the address in the browser on a phone

### Requirement: Copy the calendar URL
The copy button SHALL put the `https://` calendar URL on the clipboard and show a short confirmation. When the clipboard is not available, the URL in the field SHALL be selected so the visitor can copy it by hand.

#### Scenario: Copied
- **WHEN** a visitor clicks the copy button
- **THEN** the clipboard holds the `https://` URL of the calendar file and a "Link copied" confirmation appears for a moment

#### Scenario: Clipboard unavailable
- **WHEN** a visitor clicks the copy button and the browser denies clipboard access
- **THEN** the URL in the field is selected and no confirmation is shown

### Requirement: Telegram block
The site page SHALL have a separate Telegram block, next to the subscribe block, linking to the site's Telegram channel (for StopGame `https://t.me/sg_alerts`) with a short note that the channel announces new, rescheduled, live and canceled streams. The home page SHALL NOT have this link.

#### Scenario: Telegram link
- **WHEN** a visitor clicks the Telegram button on the StopGame page
- **THEN** `https://t.me/sg_alerts` opens

### Requirement: Calendar from the published file
The site page SHALL render an interactive calendar below the subscribe and Telegram blocks, built in the browser from the site's published calendar file, with one entry per event of the file at the times the file gives.

#### Scenario: Entries from the file
- **WHEN** the calendar file holds an event starting `20261025T150000` in `Europe/Moscow` with the default duration
- **THEN** the calendar shows the entry from 15:00 to 17:00 Moscow time, converted to the viewer's time zone

### Requirement: Calendar views
The calendar SHALL offer month, week and list views. The list view SHALL show 14 days starting today and move by 14 days. The initial view SHALL be the month view on wide screens and the list view on narrow (phone) screens. Weeks SHALL start on Monday and times SHALL use the 24-hour clock in every language.

#### Scenario: Phone
- **WHEN** a visitor opens the page on a screen narrower than about 640 px
- **THEN** the list view of the next 14 days is shown

#### Scenario: Desktop
- **WHEN** a visitor opens the page on a wide screen
- **THEN** the month view of the current month is shown, weeks starting on Monday

### Requirement: Viewer's time zone
Times SHALL be shown in the time zone of the viewer's browser, and the page SHALL name that zone near the calendar by its IANA identifier (e.g. `Europe/Berlin`), untranslated.

#### Scenario: Viewer in Berlin
- **WHEN** a viewer whose browser zone is `Europe/Berlin` opens the page in winter
- **THEN** a stream at 15:00 Moscow time is shown at 13:00 and the page says times are in `Europe/Berlin`

### Requirement: Past and live entries
Entries SHALL use the accent color. An entry whose end is before now SHALL be shown dimmed. An entry whose start is not after now and whose end is after now SHALL be marked live. Past and live state SHALL follow the entry times only, since the file carries no status.

#### Scenario: Live stream
- **WHEN** now is between an entry's start and end
- **THEN** the entry is marked live

#### Scenario: Finished stream
- **WHEN** an entry's end is before now
- **THEN** the entry is shown dimmed and not marked live

#### Scenario: Known limitation: stream longer than the default duration
- **WHEN** a stream is still running after its scheduled start plus the default duration (2 hours), so its entry in the file has already ended
- **THEN** the entry is shown dimmed and not marked live until the bot publishes the finished stream with its real end; a future change may fix this

### Requirement: Event details
Clicking an entry SHALL open a dialog with the event image, name, start and end, description, and one button per `<name>: <url>` link line of the description (for StopGame: Twitch, YouTube, StopGame), opening in a new tab. The link lines SHALL NOT repeat in the description text. The dialog SHALL close with Escape, a close button or a click outside it.

#### Scenario: Open details
- **WHEN** a visitor clicks an entry whose description ends with the Twitch, YouTube and StopGame lines
- **THEN** the dialog shows the image, name, times, the description without those lines, and Twitch, YouTube and StopGame buttons

#### Scenario: Missing parts
- **WHEN** an entry has no image and no description
- **THEN** the dialog shows the name and times without empty placeholders

### Requirement: Calendar refresh
While the page is visible, the calendar file SHALL be loaded again every 10 minutes bypassing the browser cache, and immediately when the visitor returns to the page after it was hidden. The calendar SHALL update in place without reloading the page. A failed refresh SHALL keep the entries already shown, without an error message.

#### Scenario: Schedule changed
- **WHEN** the published file gains an event while the page is open and visible
- **THEN** the entry appears within about 10 minutes plus the Pages cache time, without a page reload

#### Scenario: Refresh fails
- **WHEN** a refresh fails after the calendar was shown
- **THEN** the shown entries stay and no error is shown

### Requirement: Calendar load failure and empty state
When the calendar file cannot be loaded and no entries are shown yet, the calendar area SHALL show an error message with a retry button. A view with no entries SHALL say there are no streams.

#### Scenario: First load fails
- **WHEN** the calendar file cannot be loaded on page open
- **THEN** "Couldn't load the schedule" and a Retry button are shown, and Retry loads the file again

#### Scenario: Empty list
- **WHEN** the list view covers 14 days without events
- **THEN** it says there are no streams

### Requirement: Localized pages
Each page SHALL take its texts from its own string files, one per supported language. The home page SHALL support English; the StopGame page SHALL support English and Russian. Event names and descriptions SHALL be shown as they are in the calendar file in every language.

#### Scenario: Russian StopGame page
- **WHEN** the StopGame page is shown in Russian
- **THEN** its texts, the calendar's month and day names, view buttons and dates are in Russian, and event names and descriptions are as in the file

### Requirement: Language choice
A page SHALL use the language remembered from the visitor's manual choice if the page supports it; otherwise the first language of the browser's preferred languages, compared by primary subtag (`ru-RU` → `ru`), that the page supports; otherwise English.

#### Scenario: Russian device
- **WHEN** a visitor with browser languages `ru-RU, en` opens the StopGame page for the first time
- **THEN** the page is in Russian

#### Scenario: Unsupported language
- **WHEN** a visitor with browser language `de-DE` opens the StopGame page
- **THEN** the page is in English

#### Scenario: Home page
- **WHEN** a visitor with browser language `ru-RU` opens the home page
- **THEN** the home page is in English

### Requirement: Manual language switch
A page supporting more than one language SHALL show a language switch in its header; the choice SHALL apply at once without a page reload and SHALL be remembered in the browser for later visits. A page with one language SHALL NOT show the switch. When the browser cannot store the choice, the switch SHALL still work for the current visit.

#### Scenario: Switch to English
- **WHEN** a visitor on the Russian StopGame page picks EN and later opens the page again
- **THEN** the page switches to English at once and opens in English next time

### Requirement: Language applied to the document
The page's `lang` attribute, title and description SHALL match the shown language. The page SHALL NOT show texts in another language while its strings are loading.

#### Scenario: No flash of English
- **WHEN** a Russian visitor opens the StopGame page
- **THEN** no English interface text is visible before the Russian texts appear

### Requirement: Look and accessibility
The pages SHALL use a dark theme with a dark, not pure black, background and a red accent color, and SHALL work on phone and desktop widths without horizontal scrolling. Text SHALL meet WCAG AA contrast against its background, focused controls SHALL have a visible focus indicator, and icon-only buttons SHALL have an accessible label.

#### Scenario: Keyboard use
- **WHEN** a visitor moves through the StopGame page with Tab and opens an entry with Enter
- **THEN** each focused control is visibly marked, the details dialog opens, and Tab stays inside the dialog until it is closed
