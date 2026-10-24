# Proposal

Issue: [#14 Publish generated ICS calendars via GitHub Pages](https://github.com/viktor-235/sched-alert-bot/issues/14)

Depends on: `ics-export` (#13), which writes the site's `.ics` file to the local filesystem.

## Why

The `.ics` file written by `ics-export` exists only on the bot's server, so nobody can subscribe to it. Publishing it under a stable public URL lets calendar apps subscribe, and gives the future web calendar view (#15) a place to load it from.

## What Changes

- **A separate public repository for published calendars**, [`viktor-235/sched-alert-calendars`](https://github.com/viktor-235/sched-alert-calendars), served by GitHub Pages from `main` at the root. The bot code repository is not used:
  - the bot's token can write only to the calendars repository, so a leaked token cannot change code, `main` or release tags (a tag builds the Docker image);
  - bot commits do not mix with code history, and pushing straight to `main` of the code repository stays possible.
- **Repository layout:**
  ```
  .nojekyll            # Pages serve files as is
  index.html           # index of all calendars (#15), edited by hand, not by the bot
  stopgame/
    index.html         # the site's page, edited by hand, served at /sched-alert-calendars/stopgame/
    streams.ics        # written only by the bot
  ```
  Subscription URL: `https://viktor-235.github.io/sched-alert-calendars/stopgame/streams.ics` (`webcal://` for one-click subscribe). One folder per site keeps the page and its calendar together and leaves room for more calendars of the same site later. The file is named after its content (`<site>/<content>.ics`); `streams.ics` holds all streams of the site.
- **Optional, per-site publishing, disabled by default.** The bundled `application.yml` enables it for stopgame, like the export, so the token is required to start. Configured per site next to the export, e.g. for stopgame:

  ```yaml
  site:
    stopgame:
      ics:
        publish:
          enabled: true                      # property default: false
          repository: viktor-235/sched-alert-calendars
          branch: main
          path: stopgame/streams.ics
          min-interval: 10m                  # minimum time between commits
          token: ${CALENDAR_GITHUB_TOKEN:}
  ```
  The token is a secret: its value comes from the environment (`.env`), `application.yml` only references the variable. It is a fine-grained personal access token limited to the calendars repository with only Contents: Read and write (design decision 7).
- **How the bot commits:** through the GitHub Contents API (one `PUT` per file update, with the current file `sha`), not through a local git clone. No git binary, no clone and no extra volume in the container. Only the calendar file is touched, so hand edits of `index.html` never conflict with the bot. If the `sha` is outdated (someone changed the file), the bot reads the current one and retries.
- **When a commit is made:**
  - only when the local file differs from the published one. `ics-export` keeps an unchanged schedule byte-identical (and a running stream no longer changes the file every minute), so there are no empty commits;
  - not more often than `min-interval`. Changes made in between are published together by the next commit, which carries the latest file. Calendar clients refresh subscriptions every few hours anyway, and branch-built Pages have a soft limit of 10 builds per hour;
  - a file that was not published (failure, interval not yet passed, bot restarted) is published by a later run.
- **Failure isolation:** a publish failure (network, expired or revoked token, GitHub outage) is logged and does not stop scraping, saving, the local export or Telegram notifications. It is retried after `min-interval`, so a revoked token logs one error per interval, not one per run.
- **Calendar name.** The exported file gets an optional per-site name (`site.<source>.ics.name`, stopgame: `StopGame — Стримы`), written as `NAME` (RFC 7986) and `X-WR-CALNAME`. The file name does not include the site, so without it calendar apps show the URL or `streams.ics` on subscribe.
- **Affected statuses and users:**
  - Telegram users: no change.
  - Event statuses: no change. The published file is the local `ics-export` file as is: SCHEDULED, LIVE and FINISHED events, no CANCELED ones.
  - New audience: anyone with the URL can subscribe; the data is already public on the source site.

## Out of scope

- The web calendar view (`index.html`), issue #15.
- Creating the calendars repository and enabling Pages: a one-time manual setup, described in the README.

## Decided

- **No custom domain.** The URL stays on `viktor-235.github.io`. Subscribers keep the URL in their calendar apps and Pages cannot redirect a moved file, so the repository name (`sched-alert-calendars`) and the path (`<site>/streams.ics`) are fixed once the first link is shared.

## Capabilities

### New Capabilities
- `calendar-publishing`: per-site publishing of the exported `.ics` file to a GitHub repository served by Pages: configuration and defaults, when a commit is made (changed content, minimum interval, catching up on unpublished changes), conflict handling, and failure isolation from the rest of processing.

### Modified Capabilities
- `calendar-export`: optional calendar name written to the file. Otherwise unchanged; publishing reads its output file.

## Impact

- Code:
  - a shared publisher outside `site/` (GitHub Contents API client, change detection, interval), reused by future sources;
  - a call from the site processing after the export step.
- Config: new `site.<source>.ics.publish.*` properties, disabled by default; new optional `site.<source>.ics.name`; `CALENDAR_GITHUB_TOKEN` in `.env.example`.
- Dependencies: none; Spring's `RestClient` and Jackson are already on the classpath.
- Tests: commit only on changed content; no commit within `min-interval`; a skipped change is published later; an outdated `sha` is retried; a publish failure does not stop notifications; disabled configuration makes no API calls. The GitHub API is stubbed in tests.
- Deployment: the token in `.env`; README describes creating the calendars repository, enabling Pages, `.nojekyll` and the token permissions.
- Database: no schema change.
