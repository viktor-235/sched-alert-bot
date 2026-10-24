# Design

## Context

See proposal.md (Why) for motivation and `specs/calendar-publishing/spec.md` for the required behavior.

Current state that shapes the approach:

- `SgProcessor.process()` ends with `exportCalendar(saved)`, which rewrites the local file (`site.stopgame.ics.path`) after a run that saved something, or when the file is missing. A failure is logged and swallowed.
- `IcsWriter` output is deterministic: equal stored events give byte-identical files (`ics-export` design, decision 3). Comparing bytes is therefore a reliable change signal.
- Shared code lives in top-level packages (`calendar/`, `compare/`, `template/`, ...); per-site settings are records bound with `Binder.bindOrCreate("site.<source>.ics", ...)` in the source's `@Configuration` (`SgCalendarConfig`).
- `spring-boot-starter-webflux` is on the classpath, so the application type is reactive and Spring Boot does not auto-configure a `RestClient.Builder`. `spring-web` (with `RestClient` and `JdkClientHttpRequestFactory`) and Jackson are available.
- The scheduler runs every minute. Current time comes from the injected `Clock`.

## Goals / Non-Goals

**Goals:**
- Publish the local `.ics` file to a GitHub repository with no new dependencies and no git binary.
- At most one commit per `min-interval`, and none when the content is unchanged, including after a restart.
- Publishing failures never affect scraping, saving, the local export or notifications, and do not flood the log.
- Reusable by a future source: it only binds its own settings and calls the publisher.

**Non-Goals:**
- The web calendar view (#15) and creating the calendars repository (manual, README).
- Persisting publishing state across restarts.
- GitHub App authentication.

## Decisions

### 1. GitHub Contents API through `RestClient`

`GitHubContentsClient` wraps two calls of the [Contents API](https://docs.github.com/en/rest/repos/contents):

- `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` → the file's blob `sha`, or empty on 404.
- `PUT /repos/{owner}/{repo}/contents/{path}` with `message`, base64 `content`, `branch` and the current `sha` (omitted when the file does not exist yet) → the new blob `sha` from `content.sha`.

Headers: `Authorization: Bearer <token>`, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`. The client is a plain `RestClient` built from `RestClient.builder()` with a `JdkClientHttpRequestFactory` (connect timeout 10 s, read timeout 30 s), base URL `api-url` (default `https://api.github.com`, overridable for tests and GitHub Enterprise). Path segments are added one by one, so `/` in the configured file path is kept and other characters are encoded.

A `409 Conflict` (outdated `sha`) or `422 Unprocessable Entity` (e.g. `sha` missing for an existing file) on `PUT` is thrown as `GitHubConflictException`; every other error is left as Spring's `RestClientException`.

Alternative: a local git clone and `git push`. Rejected in the proposal: a git binary, a volume and merge handling in the container.

Alternative: `WebClient`. Rejected: the processing is synchronous; `block()` everywhere would only add noise.

Alternative: a GitHub client library (e.g. `org.kohsuke:github-api`). Rejected: a dependency for two HTTP calls.

### 2. Change detection by git blob SHA

GitHub reports a file's git blob SHA: `sha1("blob " + <length> + "\0" + <bytes>)`. The publisher computes the same SHA of the local file and compares it to the last known remote SHA. So:

- no download of the published content to compare it;
- an unchanged schedule after a restart costs one `GET`, then nothing;
- equal SHA means equal bytes, so there are no empty commits.

The remote SHA is cached in memory after every successful `GET` or `PUT`. Hand edits of the calendar file in the repository are not expected (spec: the file is written only by the bot); if one happens, the next `PUT` fails with a conflict and is retried (decision 4).

### 3. When to publish

`CalendarPublisher.publish()` is called by `SgProcessor` after every run, after the export step, whether the run saved anything or not, so a change skipped earlier is caught up by a later run. Steps:

1. Disabled → return. The local file is missing → return (export failed or is not written yet).
2. Local SHA equals the cached remote SHA → return.
3. `now < notBefore` → return (log at DEBUG). `notBefore` is set to `now + min-interval` after every publish attempt that reached GitHub, successful or not.
4. Remote SHA unknown (first attempt after start or after a failure) → `GET`; if it equals the local SHA, cache it and return without a commit and without starting the interval.
5. `PUT`, cache the returned SHA, log at INFO.

The interval also applies after a failure. An expired token or a GitHub outage then produces one error per `min-interval` (default 10 min) instead of one per minute, and retries continue on their own.

`notBefore` is in memory: after a restart the first changed file is published at once. Restarts are rare, so this does not threaten the Pages build limit.

Alternative: publish only after a run that wrote the file. Rejected: a failed or throttled publish would wait for the next saved change, which may take days.

Alternative: a separate `@Scheduled` publisher. Rejected: one more cron to configure; the per-run call costs one SHA of a small file when nothing changed.

### 4. Conflict handling

On `GitHubConflictException` the publisher re-reads the remote SHA with `GET`. If it now equals the local SHA, the file is already published; otherwise it retries `PUT` once with the new SHA. A second conflict is an ordinary failure (decision 5).

### 5. Failure isolation

- `publish()` catches every exception, logs it at ERROR with the repository and path, forgets the cached remote SHA (the next attempt re-reads it) and sets `notBefore`.
- `SgProcessor` calls the publisher in its own `try/catch` after `exportCalendar`, like the export. Saving and notifications have already happened by then.

### 6. Configuration

`calendar/IcsPublishProperties`, a record bound per site from `site.<source>.ics.publish` with `Binder.bindOrCreate`, like `IcsExportProperties`:

| Property | Default | Notes |
|---|---|---|
| `enabled` | `false` | |
| `repository` | — | `owner/name`, required when enabled |
| `branch` | `main` | |
| `path` | — | file path in the repository, required when enabled, no leading `/` |
| `min-interval` | `10m` | |
| `token` | — | required when enabled; `application.yml` sets `${CALENDAR_GITHUB_TOKEN:}`, the value lives in `.env` |
| `api-url` | `https://api.github.com` | |
| `commit-message` | `Update calendar` | |

- The compact constructor checks the required settings when enabled, so the application fails at startup with a clear message. The `repository` format is checked too.
- `CalendarPublisher` fails at construction when publishing is enabled but the export is not: there would be nothing to publish.
- `toString()` of the record masks the token, so it never reaches logs.
- One token variable for all sites: a future source can bind its own, but the calendars repository is meant to be shared.

### 7. Token type

A fine-grained personal access token with access to the calendars repository only and the single permission Contents: Read and write. It is a bearer token in one header; a GitHub App would need a private key, JWT signing and installation token refresh, which is more code than the whole feature. The cost is rotation: the token expires (at most a year), and expiry shows up as a logged error every `min-interval`. The README describes how to create and rotate it.

### 8. Package layout

```
calendar/
  IcsPublishProperties    record: settings, validation, token masked in toString()
  GitHubContentsClient    GET sha / PUT content, GitHubConflictException
  CalendarPublisher       change detection, interval, conflict retry, failure isolation; one instance per site
site/stopgame/
  SgCalendarConfig        binds site.stopgame.ics.publish, creates the publisher bean
  SgProcessor             calls publish() after the export
```

`CalendarPublisher` takes the client in its constructor (a mock in unit tests); a static factory builds the real client from the properties. When publishing is disabled no client is built.

### 9. Tests

Integration tests force `site.stopgame.ics.publish.enabled=false` and disable the scheduler cron (`-`) in `AbstractIntegrationTest`, so they never call GitHub or the real site, whatever the bundled `application.yml` enables.

- `IcsPublishPropertiesTest` (`Binder`): defaults, parsing of `min-interval`, failure naming missing settings when enabled, invalid `repository`, token not in `toString()`.
- `GitHubContentsClientTest` (`MockRestServiceServer` bound to the `RestClient.Builder`): request URL, headers and JSON body, `sha` omitted for a new file, 404 → empty, 409 / 422 → `GitHubConflictException`, other errors propagate.
- `CalendarPublisherTest` (mocked client, `MutableClock`, `@TempDir`):
  - disabled → no client calls;
  - no local file → no calls;
  - first run with remote equal to local → `GET` only, no `PUT`;
  - changed content → `PUT` with the cached SHA; unchanged afterwards → no calls;
  - a second change within `min-interval` → no `PUT`; after the interval → one `PUT` with the latest content;
  - conflict → `GET` and one retried `PUT`;
  - failure → logged, nothing thrown, next attempt only after the interval, then published;
  - blob SHA matches git's (`git hash-object` of a known string).
- `SgIntegrationTest`: the publisher is called on a run without saves; a publisher failure still saves and notifies.

### 10. URL layout and calendar name

One folder per site in the calendars repository: `<site>/cal.ics` is written by the bot, `<site>/index.html` (the site's page) and the root `index.html` (index of all sites) are edited by hand. Pages serve the folder at `/sched-alert-calendars/<site>/` and redirect the form without the slash, so the page and its calendar share one URL prefix, and more calendars of the same site can be added next to `cal.ics`. The path is a configuration value; the code does not depend on the layout.

The short file name says nothing on its own, so the export writes the calendar name: optional `site.<source>.ics.name` in `IcsExportProperties`, written by `IcsWriter` as `NAME` (RFC 7986) and `X-WR-CALNAME` (read by Google Calendar and Apple Calendar) after `CALSCALE`. A missing or blank name writes neither, so existing golden files are unchanged.

## Risks / Trade-offs

- [The token expires and publishing silently stops] → Logged at ERROR every `min-interval`; admin alerts (`admin-alerts` change) can pick it up later. The README notes the expiry date to watch.
- [A commit every `min-interval` while a schedule changes often] → At most 6 commits and Pages builds per hour with the default 10 minutes, leaving room under the soft limit of 10 for manual commits (e.g. `index.html`, #15). A shorter interval gives subscribers nothing: the Pages CDN caches the file for about 10 minutes, and calendar clients refresh every few hours. Calendar history grows by one small commit per change; acceptable for a data repository.
- [Pages are cached by a CDN for about 10 minutes] → Calendar clients refresh every few hours anyway.
- [In-memory state is lost on restart] → One extra `GET` after start; an unpublished change is published by the first run.
- [Contents API `GET` returns no content for files over 1 MB] → Only the `sha` is read, which is returned for files up to 100 MB. The 3-year calendar is far smaller.

## Migration Plan

1. The property default is disabled, but the bundled `application.yml` enables publishing for stopgame (`repository: viktor-235/sched-alert-calendars`, `path: stopgame/cal.ics`), like the export. Before deploying: the calendars repository has `.nojekyll` on `main`, Pages are enabled from `main` at the root, and `CALENDAR_GITHUB_TOKEN` is set in `.env`. Without the token the application does not start.
2. To deploy without publishing, set `SITE_STOPGAME_ICS_PUBLISH_ENABLED=false`.
3. Rollback: set `enabled=false`; the published file stays as it was. No data migration either way.

## Open Questions

- None. No custom domain; the URL layout is decision 10.
