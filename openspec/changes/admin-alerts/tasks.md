# Tasks

## 1. Admin users

- [ ] 1.1 Add `boolean admin` to `TelegramUser` and `findByAdminTrue()` to `TelegramUserRepository`; verify with an integration test that a stored document without `admin` reads as `false` and that `findByAdminTrue()` returns only flagged users
- [ ] 1.2 Make `handleStart` keep the existing `admin` flag; verify with a test that an admin who sends `/start` is still an admin and a new user is not
- [ ] 1.3 Add `TelegramService.sendToAdmins(String)`: sends to each admin's `id` (not `targetChatId`), or logs at WARN when there are no admins; verify with a test covering two admins with a channel as target chat, and no admins

## 2. Health tracking and alerts

- [ ] 2.1 Add `FailureType` enum with `of(Throwable)` as in design.md; verify with unit tests covering HTTP 403/429/503 → BLOCKED, HTTP 404 and `SocketTimeoutException` → NETWORK, both nested the way `XpathScraper` wraps them, plain `ScraperException` → BROKEN_PAGE, and `RuntimeException` → INTERNAL
- [ ] 2.2 Add `SourceHealthService.run(source, task)` with the state table, alert texts and metrics from design.md, and `alerts.reminder-interval: 6h` in `application.yml`; verify with unit tests using `MutableClock`, a mocked `TelegramService` and a `SimpleMeterRegistry`: first failure → alert; same type → silent; type change → alert; reminder after the interval and not before; success after failure → recovery with duration; success after success → silent; the task exception is not rethrown; an error containing `<` is escaped; counter and gauge match the metrics scenarios

## 3. Wire the stopgame scheduler

- [ ] 3.1 Make `SgScheduler` call `health.run("stopgame", processor::process)`; verify with an integration test in `SgIntegrationTest` with one admin and one regular user: `XpathScraper.parseDocument` spied to throw an `HttpStatusException` 429 → the admin's private chat gets one BLOCKED alert, the regular user gets nothing, and no events are saved; then `a-b-c.html` is served → the admin gets one recovery alert and both users get the usual new-event notifications

## 4. Documentation and checks

- [ ] 4.1 Update README: check off "Send critical exceptions to admins", add the `mongosh` command to grant admin, `alerts.reminder-interval`, and the suggested Grafana rule; remove the unused `CREATOR_ID` from `.env.example`; verify names match the code and `application.yml`
- [ ] 4.2 In `guard-against-broken-scraping/proposal.md`, answer the open question "How should failures be surfaced?" with a reference to `admin-alerts`; verify `openspec validate guard-against-broken-scraping` passes
- [ ] 4.3 Run `openspec validate admin-alerts --strict` and `./gradlew test`; verify both pass
