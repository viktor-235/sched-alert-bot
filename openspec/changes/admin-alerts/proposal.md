# Proposal

## Why

When a scheduled run fails, the exception only reaches the logs. Nobody notices that the bot has stopped working until subscribers notice missing notifications. This happens when stopgame.ru blocks the bot (HTTP 403/429/503), when the page cannot be fetched, or when the markup breaks (`ScraperException`). The risk grew with the scheduler cron moving to every minute (`0 * * * * *`): more requests make rate limiting more likely, and logging alone would produce 60 identical errors per hour. This is the README TODO "Send critical exceptions to admins", and the open question "How should failures be surfaced?" in `guard-against-broken-scraping`.

## What Changes

- A Telegram user gets an `admin` flag stored in the database (default `false`). It is set manually in MongoDB. Several users can be admins. `/start` keeps the flag instead of resetting it.
- Every scheduled source run reports success or failure. Failures are classified as: site blocks the bot (HTTP 403, 429, 503), network or other HTTP error, broken page (scraping error), or internal error (anything else, for example MongoDB).
- All admins receive a Telegram message in their private chat with the bot on the first failed run of a source. They also receive a message when the failure type changes, a reminder while it keeps failing (default every 6 hours), and a message when the source recovers. No message is sent for every further failed run.
- Two metrics are exposed through the existing Prometheus endpoint: a run counter by source and result, and the timestamp of the last successful run. These let an external alert (Grafana) detect that the application itself is down or stuck, which an in-app alert cannot report.
- Affected users: only Telegram users with `admin: true` receive the new messages. Other subscribers and their event notifications are unchanged. No event status (NEW, SCHEDULED, LIVE, FINISHED, CANCELED) or status transition changes.

## Capabilities

### New Capabilities
- `admin-alerts`: who the bot administrators are, how failures of scheduled source runs are classified, when administrators are alerted about them and about recovery, and which health metrics are exposed.

### Modified Capabilities
<!-- none: event-lifecycle and event-notifications requirements do not change -->

## Impact

- Code: `TelegramUser` (new `admin` field), `TelegramUserRepository` (find admins), `TelegramService` (keep flag on `/start`, message admins), a new shared health component, `SgScheduler` reports each run through it. No change to `SgProcessor` logic.
- Config: reminder interval in `application.yml`; unused `CREATOR_ID` removed from `.env.example`.
- Metrics: new Micrometer counter and gauge on `/actuator/prometheus`.
- Docs: README TODO checked off; README shows how to grant admin in MongoDB.
- Database: new optional field `admin` in `users`. A missing field reads as `false`, so no migration is needed.
- Out of scope: backoff or pausing requests after a block, a bot command to grant admin, enforcing the `adminOnly` flag of `/status`, and the Grafana alert rule itself.
