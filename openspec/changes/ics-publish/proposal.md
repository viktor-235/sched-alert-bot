# Proposal

Issue: [#14 Publish generated ICS calendars via GitHub Pages](https://github.com/viktor-235/sched-alert-bot/issues/14)

Depends on: `ics-export` (#13), which writes the site's `.ics` file to the local filesystem.

## Why

The `.ics` file written by `ics-export` exists only on the bot's server, so nobody can subscribe to it. Publishing it under a stable public URL lets calendar apps subscribe, and gives the future web calendar view (#15) a place to load it from.

## What Changes

- **A separate public repository for published calendars**, e.g. `viktor-235/sched-calendars`, served by GitHub Pages from `main` at the root. The bot code repository is not used:
  - the bot's token can write only to the calendars repository, so a leaked token cannot change code, `main` or release tags (a tag builds the Docker image);
  - bot commits do not mix with code history, and pushing straight to `main` of the code repository stays possible.
- **Repository layout:**
  ```
  .nojekyll            # Pages serve files as is
  index.html           # web calendar view (#15), edited by hand, not by the bot
  calendars/
    stopgame.ics       # written only by the bot
  ```
  Subscription URL: `https://viktor-235.github.io/sched-calendars/calendars/stopgame.ics` (`webcal://` for one-click subscribe).
- **Optional, per-site publishing, disabled by default.** Configured per site next to the export, e.g. for stopgame:

  ```yaml
  site:
    stopgame:
      ics:
        publish:
          enabled: false                     # default
          repository: viktor-235/sched-calendars
          branch: main
          path: calendars/stopgame.ics
          min-interval: 15m                  # minimum time between commits
  ```
  The token is a secret and comes from the environment (`.env`), not from `application.yml`.
- **How the bot commits:** through the GitHub Contents API (one `PUT` per file update, with the current file `sha`), not through a local git clone. No git binary, no clone and no extra volume in the container. Only the calendar file is touched, so hand edits of `index.html` never conflict with the bot. If the `sha` is outdated (someone changed the file), the bot reads the current one and retries.
- **When a commit is made:**
  - only when the local file differs from the published one. `ics-export` keeps an unchanged schedule byte-identical (and a running stream no longer changes the file every minute), so there are no empty commits;
  - not more often than `min-interval`. Changes made in between are published together by the next commit, which carries the latest file. Calendar clients refresh subscriptions every few hours anyway, and branch-built Pages have a soft limit of 10 builds per hour;
  - a file that was not published (failure, interval not yet passed, bot restarted) is published by a later run.
- **Failure isolation:** a publish failure (network, expired or revoked token, GitHub outage) is logged and does not stop scraping, saving, the local export or Telegram notifications.
- **Affected statuses and users:**
  - Telegram users: no change.
  - Event statuses: no change. The published file is the local `ics-export` file as is: SCHEDULED, LIVE and FINISHED events, no CANCELED ones.
  - New audience: anyone with the URL can subscribe; the data is already public on the source site.

## Out of scope

- The web calendar view (`index.html`), issue #15.
- Creating the calendars repository and enabling Pages: a one-time manual setup, described in the README.

## Open questions

- **Final URL, decided before the first public link.** Subscribers keep the URL in their calendar apps, so the path should not change after publishing. To decide:
  - the repository name (`sched-calendars` is a placeholder) and the path inside it (`calendars/<site>.ics`);
  - whether to use a custom domain now. If one is added later, Pages redirect from `github.io` to it, but a redirect still depends on GitHub keeping it, and the path must stay the same.
- **Token type:** a fine-grained personal access token limited to the calendars repository with only Contents: Read and write (simple, but it expires and needs rotation), or a GitHub App installed only on that repository (no manual rotation, more setup). Decided in design.

## Capabilities

### New Capabilities
- `calendar-publishing`: per-site publishing of the exported `.ics` file to a GitHub repository served by Pages: configuration and defaults, when a commit is made (changed content, minimum interval, catching up on unpublished changes), conflict handling, and failure isolation from the rest of processing.

### Modified Capabilities
- None. `calendar-export` (introduced by `ics-export`) does not change; publishing reads its output file.

## Impact

- Code:
  - a shared publisher outside `site/` (GitHub Contents API client, change detection, interval), reused by future sources;
  - a call from the site processing after the export step.
- Config: new `site.<source>.ics.publish.*` properties, disabled by default; a GitHub token variable in `.env.example`.
- Dependencies: none expected; the HTTP client already available in Spring is enough. Decided in design.
- Tests: commit only on changed content; no commit within `min-interval`; a skipped change is published later; an outdated `sha` is retried; a publish failure does not stop notifications; disabled configuration makes no API calls. The GitHub API is stubbed in tests.
- Deployment: the token in `.env`; README describes creating the calendars repository, enabling Pages, `.nojekyll` and the token permissions.
- Database: no schema change.
