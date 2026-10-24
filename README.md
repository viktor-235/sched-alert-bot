# sched-alert-bot

## Description
The `sched-alert-bot` project is designed to parse event schedules from various websites and send notifications via Telegram.  
It was originally developed to parse the streaming schedule from stopgame.ru, but it's implemented in a flexible way to support additional sources.

## Technologies
- Java
- Spring Boot
- MongoDB
- [jsoup](https://jsoup.org/) for parsing web pages
- [JaVers](https://javers.org/) for detecting changes
- [FreeMarker](https://freemarker.apache.org/) for generating messages from templates
- [TelegramBots](https://github.com/rubenlagus/TelegramBots) for notifying users
- [iCal4j](https://github.com/ical4j/ical4j) for the calendar export

## Configuration
Application settings are located in the `resources/application.properties` file and the `.env` file.  
Make sure you have configured the MongoDB connection and Telegram API credentials (`BOT_TOKEN`, `BOT_NAME`).  
Telegram is optional: without `BOT_TOKEN` the bot is disabled and no messages are sent, while events are still scraped, saved and exported to the calendar. `docker compose` works without `.env` in that case.

### Calendar export
Each site can write its schedule to an iCalendar (`.ics`) file that calendar apps can import or subscribe to. The export is off unless `enabled` is set; the bundled `application.yml` enables it for stopgame. Settings for stopgame are under `site.stopgame.ics` in `application.yml`:

| Setting | Default | Description |
|---|---|---|
| `enabled` | `false` | Write the calendar file |
| `path` | `./ics/stopgame/streams.ics` | File location; missing directories are created |
| `default-event-duration` | `120m` | Event duration when the real one is unknown |
| `past-period` | `3y` | How far back events are kept in the file |
| `url` | `https://www.twitch.tv/stopgameru` | Stream link added to every event |
| `time-zone` | `Europe/Moscow` | Zone of event times in the file, e.g. the zone the site announces times in. Calendar apps still show events in their own zone; Apple Calendar also shows this zone's time. When not set, times are written in UTC |
| `name` | `StopGame — Стримы` | Calendar name shown by calendar apps on subscribe (`NAME` and `X-WR-CALNAME`). When not set, apps show the URL or the file name |

`path`, `url` and `default-event-duration` are required when the export is enabled; the application does not start without them. Any setting can be overridden with an environment variable, e.g. `SITE_STOPGAME_ICS_ENABLED=true`.

The file holds the stored scheduled, live and finished events and is rewritten after every run that saves a change, or when it is missing. Canceled events are removed from the file: subscribed calendars drop them on refresh, while a calendar that imported the file once keeps them. Scheduled and live events use the scheduled time and the default duration; finished streams use their real start and end. Times are written in `time-zone`, or in UTC when it is not set. The event description (text, participants and links to Twitch, YouTube and StopGame) comes from the FreeMarker template `src/main/resources/templates/stopgame-calendar.txt.ftl`; the links are in the description because Google Calendar ignores the event `URL`.

In Docker the app writes to `/app/ics`; `docker-compose.yml` mounts it as `./ics`.

### Calendar publishing
The exported file can be published to a separate GitHub repository served by GitHub Pages, so calendar apps can subscribe to it by URL. The bot commits through the GitHub Contents API and changes only its own calendar file. Publishing is off unless `enabled` is set; the bundled `application.yml` enables it for stopgame, so `CALENDAR_GITHUB_TOKEN` must be set, or publishing turned off with `SITE_STOPGAME_ICS_PUBLISH_ENABLED=false`. Settings for stopgame are under `site.stopgame.ics.publish` in `application.yml`:

| Setting | Default | Description |
|---|---|---|
| `enabled` | `false` | Publish the calendar file; requires the calendar export to be enabled |
| `repository` | `viktor-235/sched-alert-calendars` | Target repository, `owner/name` |
| `branch` | `main` | Target branch |
| `path` | `stopgame/streams.ics` | File path in the repository |
| `min-interval` | `10m` | Minimum time between publish attempts |
| `token` | `${CALENDAR_GITHUB_TOKEN}` | GitHub token; set `CALENDAR_GITHUB_TOKEN` in `.env` |
| `api-url` | `https://api.github.com` | GitHub REST API base URL |
| `commit-message` | `Update calendar` | Message of the bot's commits |

`repository`, `path` and `token` are required when publishing is enabled; the application does not start without them.

After each run the file is committed if it differs from the published one and at least `min-interval` has passed since the last attempt; changes made in between go into the next commit. An unchanged file is never committed, also after a restart. A failure (network, expired token, GitHub outage) is logged and retried after `min-interval`; it does not affect saving, the local export or notifications.

One-time setup:
1. Create a public repository for the calendars ([viktor-235/sched-alert-calendars](https://github.com/viktor-235/sched-alert-calendars)) and commit an empty `.nojekyll` file to the root of `main`, so Pages serve files as is. The repository must not be empty: the bot writes to an existing branch.
2. In the repository settings → Pages, deploy from the `main` branch, root folder.
3. Create a [fine-grained personal access token](https://github.com/settings/personal-access-tokens/new) with access to that repository only and the single permission Contents: Read and write. Put it into `.env` as `CALENDAR_GITHUB_TOKEN`. The token expires: note the date and replace it before then; an expired token shows up as `Failed to publish calendar` errors in the log.

The calendar is then available at `https://viktor-235.github.io/sched-alert-calendars/stopgame/streams.ics`. Use `webcal://viktor-235.github.io/sched-alert-calendars/stopgame/streams.ics` for a one-click subscribe link. Each site gets its own folder, with the calendar file named after its content (`<site>/<content>.ics`), so a page for the site can sit next to it as `<site>/index.html` and be served at `/sched-alert-calendars/<site>/`. Keep the URL stable once shared: subscribers keep it in their calendar apps, and Pages cannot redirect them to a new one.

## Development
The entry point is a scheduler class, e.g., `SgScheduler`.

Workflow from task to release, commit and PR conventions: [CONTRIBUTING.md](CONTRIBUTING.md).

## TODO
- [x] Add automatic Docker image build
- [x] Set up MongoDB audit
- [x] Format date (currently just in MSK timezone)
- [ ] Request user timezone via the bot and use it when displaying dates. This requires generating messages individually for each user
- [x] Escape values when formatting messages
- [ ] Send critical exceptions to admins
- [ ] Retry page fetch on transient network errors (e.g. connect timeout), so a single failure does not trigger an admin alert
- [ ] Move configuration to the database, with default values in properties
- [x] Rewrite using XPath if it allows attribute extraction
- [x] Subscribe to calendar via link
- [x] Add Twitch/YouTube links during live streams
- [ ] Prevent sending past events
- [x] Add stream cover image
- [x] Telegram command `/status`
- [ ] Telegram command to trigger check manually (without waiting for cron)
- [x] Notify about canceled streams  
