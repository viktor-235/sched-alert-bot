# Design

## Context

- `SgScheduler.performProcessing()` calls `SgProcessor.process()` directly. Any exception propagates to Spring's `ThreadPoolTaskScheduler` (`SchedulerConfig`), whose default error handler only logs it.
- `XpathScraper.parseDocument` wraps every Jsoup `IOException` into `ScraperException`, and `parsePage` wraps that into another `ScraperException`. The HTTP status survives only as `org.jsoup.HttpStatusException` somewhere in the cause chain. XPath and parsing failures are `ScraperException` without an `IOException` cause. `guard-against-broken-scraping` adds more `ScraperException`s of the second kind.
- `AbstractTelegramService.sendMessage(chatId, text)` already catches `TelegramApiException` and logs it, so a failed send never propagates.
- `TelegramUser.id` is the Telegram user id, which is also the id of the user's private chat with the bot. `handleStart` builds a fresh `TelegramUser` and saves it, replacing the stored document.
- There is no admin concept: `Command.adminOnly` exists but is never checked, and `CREATOR_ID` in `.env.example` is not read anywhere.
- Micrometer with the Prometheus registry is already on the classpath and `/actuator/prometheus` is exposed.
- Single instance; the injected `Clock` is the only time source.

## Goals / Non-Goals

**Goals:**
- Keep the implementation small: one enum and one service, plus small edits to existing classes.
- One shared mechanism that any future source scheduler reuses with a single call.
- Throttling is unit-testable with `MutableClock`, without Spring or Telegram.

**Non-Goals:**
- Pausing or backing off requests after BLOCKED. That changes scraping behavior and deserves its own change.
- A bot command to grant or revoke admin, and enforcing `adminOnly` for `/status`. Both become easy once the flag exists, but they change user-facing commands.
- Persisting health state across restarts.
- Shipping a Grafana alert rule or dashboard. The README documents a suggested rule.
- Reading `Retry-After`: `HttpStatusException` does not expose response headers.

## Decisions

### Admins are a flag on `TelegramUser`
Add `@Field private boolean admin;` to `TelegramUser` and `List<TelegramUser> findByAdminTrue()` to `TelegramUserRepository`. Admins are granted manually: `db.users.updateOne({_id: "<telegram id>"}, {$set: {admin: true}})`, documented in the README.

- Alternative: an `ADMIN_CHAT_ID` environment variable. Rejected: it allows only one recipient and needs a redeploy to change.
- No Mongock migration: the field is optional, and a missing value maps to `false` on the primitive `boolean`.
- `handleStart` reads the existing user and copies its `admin` flag into the new document; otherwise re-running `/start` would silently drop it.

### Alerts go to the private chat
`TelegramService.sendToAdmins(String text)` sends to `user.getId()` of every admin. `targetChatId` can be a public channel, so it is never used for alerts. With no admins, the text is logged at WARN.

### Wrap the run explicitly
`SourceHealthService.run(String source, Runnable task)` runs the task, records `ok` or the classified failure, sends alerts, and logs the exception instead of rethrowing it. `SgScheduler` calls `health.run("stopgame", processor::process)`.

- Alternative: `ThreadPoolTaskScheduler.setErrorHandler`. Rejected because it sees only failures; recovery and the last-success metric need the success signal.

A run skipped because no users are registered ends without an exception, so it counts as `ok`.

### Classification in the enum
`enum FailureType { BLOCKED, NETWORK, BROKEN_PAGE, INTERNAL; static FailureType of(Throwable e) }` walks `getCause()`:
1. `HttpStatusException` with status 403, 429 or 503 → BLOCKED; any other status → NETWORK.
2. Any other `IOException` → NETWORK.
3. Otherwise, if the top-level exception or any cause is `ScraperException` → BROKEN_PAGE.
4. Otherwise → INTERNAL.

- Alternative: `ScraperException` subclasses thrown by `XpathScraper`. This is cleaner long term, but it touches every throw site and overlaps with `guard-against-broken-scraping`.

### Minimal per-source state
`Map<String, Failure>`, where `record Failure(FailureType type, Instant since, Instant lastAlertAt)`; no entry means healthy.

| Run | State | Action |
|---|---|---|
| failure | healthy | alert, store `Failure(type, now, now)` |
| failure | failing, other type | alert, store new type, `lastAlertAt = now` (keep `since`) |
| failure | failing, same type, `now - lastAlertAt ≥ reminder` | reminder, `lastAlertAt = now` |
| failure | failing, otherwise | nothing |
| success | failing | recovery alert with `now - since`, remove entry |
| success | healthy | nothing |

Runs of one source never overlap, so a `ConcurrentHashMap` is enough.

### Plain text, no template
Alerts are three short Russian strings built with `formatted()` in `SourceHealthService`, for example `🔴 stopgame: BLOCKED\n<code>HTTP error fetching URL. Status=429</code>`. The error is escaped with `HtmlUtils.htmlEscape` and cut to 500 characters. A FreeMarker template is not worth it for three one-liners for admins.

### Metrics
In `SourceHealthService`, via `MeterRegistry`:
- `sched_alert_source_runs_total{source, result}`: a counter, result ∈ `ok|blocked|network|broken_page|internal`.
- `sched_alert_source_last_success_seconds{source}`: a gauge backed by an `AtomicLong` per source, set from `clock.instant()`.

Suggested Grafana rule (documentation only): `time() - sched_alert_source_last_success_seconds > 1800`.

### Configuration
```yaml
alerts:
  reminder-interval: 6h
```

## Risks / Trade-offs

- [Alerting on the first failure with a one-minute cron means a single timeout produces a failure alert followed by a recovery alert] → Accepted on purpose, so that short outages are not missed. If it becomes noisy, a threshold can be added later without changing the state shape.
- [The restart loses state] → A source still broken after a restart sends a new failure alert, but no recovery for the earlier outage. This is acceptable.
- [A Cloudflare challenge page answers 200 and is classified BROKEN_PAGE, not BLOCKED] → The error message is in the alert.
- [If Telegram itself is down, alerts are lost] → They are logged; the last-success metric plus an external Grafana rule cover this case.
- [The admin flag is set by hand in MongoDB] → The README has the exact command.

## Migration Plan

Deploy, then set `admin: true` for the desired users in MongoDB. Until then, the only differences are WARN logs on failures and the new metrics. Rollback is a plain redeploy of the previous image; the extra `admin` field is ignored by the old code.
