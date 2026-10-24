# Tasks

## 1. Configuration

- [x] 1.1 Create `calendar/IcsPublishProperties` (design decision 6) with defaults `enabled=false`, `branch=main`, `min-interval=10m`, `api-url=https://api.github.com`, `commit-message=Update calendar`; validation of `repository`, `path`, `token` when enabled; `toString()` masking the token; a static `bind(Binder, prefix)`. Verify: `IcsPublishPropertiesTest` covers the defaults, parsing of `min-interval`, the failure naming missing settings, an invalid `repository`, and the token absent from `toString()`.
- [x] 1.2 Add `site.stopgame.ics.publish` to `application.yml` (disabled, `token: ${CALENDAR_GITHUB_TOKEN:}`, commented example repository and path) and `CALENDAR_GITHUB_TOKEN=` to `.env.example`. Verify: the application context of `SgIntegrationTest` starts with publishing disabled.

## 2. GitHub client

- [x] 2.1 Implement `calendar/GitHubContentsClient` and `GitHubConflictException` (design decision 1). Verify: `GitHubContentsClientTest` with `MockRestServiceServer` covers the GET/PUT URLs with a nested path, auth and API headers, the PUT JSON body (base64 content, branch, message, `sha` present or omitted), 404 → empty, 409 and 422 → `GitHubConflictException`, other errors propagate.

## 3. Publisher

- [x] 3.1 Implement `calendar/CalendarPublisher` (design decisions 2–5): git blob SHA, cached remote SHA, `notBefore` from the injected `Clock`, conflict retry, failure isolation, startup check that the export is enabled, factory building the client only when enabled. Verify: `CalendarPublisherTest` covers every case listed in design decision 9.
- [x] 3.2 Create the stopgame publisher bean in `SgCalendarConfig` and call `publish()` from `SgProcessor.process()` after `exportCalendar`, in its own `try/catch`; update `SgProcessorTest` construction. Verify: `SgIntegrationTest` cases: the publisher is called on a run that saves nothing; a publisher failure still saves events, writes the local file and sends notifications.
- [x] 3.3 Isolate integration tests from the bundled configuration: `AbstractIntegrationTest` sets `site.stopgame.ics.publish.enabled=false` and `site.stopgame.scheduler.cron=-`. Verify: `./gradlew test` passes with `enabled: true` in `application.yml`, and no scheduled run reaches the real site during tests.

## 4. Docs

- [x] 4.1 README: publishing section (settings table, creating the calendars repository with `.nojekyll`, enabling Pages, fine-grained token with Contents: Read and write on that repository only, rotation, subscription URL and `webcal://`); check off "Subscribe to calendar via link". Verify: the settings in the README match `IcsPublishProperties` and `application.yml`.

## 5. URL layout and calendar name

- [x] 5.1 Publish stopgame to `stopgame/streams.ics` and write the local file to `./ics/stopgame/streams.ics` (design decision 10): `application.yml`, README, proposal. Verify: the README URLs and settings table match `application.yml`.
- [x] 5.2 Add the optional `name` to `IcsExportProperties`, write it in `IcsWriter` as `NAME` and `X-WR-CALNAME`, pass it from `CalendarExporter`; `name: StopGame — Стримы` in `application.yml`. Verify: `IcsWriterTest` covers a name with an escaped comma and a blank name writing nothing; `IcsExportPropertiesTest` covers the unset and bound name; the golden files are unchanged.

## 6. Verification

- [x] 6.1 Run `./gradlew test`. Verify: all tests pass.
