# Proposal

## Why

The event status model introduced on `develop` (SCHEDULED / LIVE / FINISHED / CANCELED, replacing `nowLive`) has gaps that cause wrong or missing notifications and inconsistent stored state: status-only transitions are dropped because "changed" is computed from field diffs alone, ending streams produce a broken "update" message, and existing production data (from `v1.1.0`) has no `status`, so it falls out of processing. These must be fixed before `v1.2.0` is released to production.

An attempted spot fix on branch `integration-tests-and-bug-fixes` (keep the stored `date` of a LIVE event, because the site shows no date while live) confirmed that the gaps cannot be fixed one field at a time. Run repeatedly against the same page, it caused two regressions:
- "🔴 В эфире" was re-sent on every cron run while the stream was live, and `startedAt` was overwritten each time.
- A FINISHED event that returned to the site was silently dropped and stayed FINISHED.

Both happen because the stored data and the site never match in a stable way. Status transitions only got through by accident, as a side effect of field diffs.

## What Changes

- A status transition is persisted even when no event field changed; deciding *whether to save* is separated from deciding *whether to notify*.
- Processing is idempotent: running again against an unchanged page saves nothing and sends nothing, in every status (LIVE included).
- The scheduled `date` of an event is kept while it is LIVE. The site shows no date during a stream, and that absence is not treated as a field change.
- `date` holds the last date the site explicitly showed in the schedule. It is overwritten only by a non-empty date from the site and is never cleared, so FINISHED and CANCELED records keep it for later lookup.
- An event first seen already LIVE has no `date`, because the site never showed one; its actual start time is in `startedAt`.
- Notification rules are defined per transition (all subscribed Telegram users are affected equally):
  - new event → SCHEDULED: "🆕 Новое событие" (full card)
  - new event → LIVE: "🔴 В эфире" (full card)
  - SCHEDULED with changed fields: "🆙 Обновление события" + diff
  - SCHEDULED → LIVE, LIVE with changed fields: "🔴 В эфире" + diff
  - LIVE → FINISHED (event disappeared from the site): **no notification**
  - SCHEDULED → CANCELED (event disappeared from the site while its `date >= now`): "❌ Отмена события"
  - SCHEDULED → FINISHED (event disappeared from the site while its `date < now`, or `date` is null): **no notification**. The stream most likely aired without the bot seeing it live (bot offline, site never marked it live, or the stream was shorter than one cron interval). A real cancellation after the start time is not notified; this is an accepted limitation, because a false "❌" for a stream that aired is worse than a missing late one. This is the same rule as the migration below.
  - CANCELED / FINISHED → back on site, not live: new header "🔁 Снова в расписании" + diff, sent even when no field changed
  - CANCELED / FINISHED → back on site, live: "🔴 В эфире" + diff
  - SCHEDULED / LIVE with no status change and no changed fields: not saved, no notification
  - LIVE → on site but not live: becomes SCHEDULED via the generic rules (not observed on stopgame.ru; a later false cancellation is an accepted limitation)
- Stream timing fields are used only to compute stream duration:
  - entering LIVE from any non-LIVE state sets `startedAt = now` and clears `endedAt`
  - LIVE → FINISHED sets `endedAt = now`
  - every other transition leaves both fields untouched (including CANCELED, and SCHEDULED → FINISHED, where both stay empty because the duration is unknown)
- A Mongock migration converts `v1.1.0` records:
  - `nowLive = true` → FINISHED
  - `nowLive = false` and `date >= now` → SCHEDULED
  - `nowLive = false` and `date < now` → FINISHED
  - `nowLive = false` and `date` is null → FINISHED
  - `nowLive` is removed
  - `startedAt` / `endedAt` stay empty, because the real values are unknown
  - records that were live under `v1.1.0` have `date = null`, because `v1.1.0` cleared it while live; it cannot be restored
  - Consequences accepted: on the first run, stale scheduled events no longer on the site produce late "❌" notifications, and a stream that is live at deploy time is announced as "🔴" once more.
- Duplicate event ids on a scraped page no longer abort the whole processing run: the first occurrence is kept, a warning is logged, and processing continues.
- An empty value in a diff is still marked, but rendered as italic "пусто" (`<i>пусто</i>`) instead of the literal `<пусто>`, which Telegram rejects in HTML parse mode as an unknown tag. The marker is defined once in the template.

Out of scope (separate changes):
- HTML-escaping of scraped values in templates
- time zone shown by the `/status` command
- per-source user subscriptions

## Capabilities

### New Capabilities
- `event-lifecycle`: event statuses and transitions derived from what the scraper observes, persisted state (`status`, `startedAt`, `endedAt`), and migration of legacy records.
- `event-notifications`: which lifecycle transitions notify subscribers, and the message header and content (full card vs. diff) for each.

### Modified Capabilities
<!-- none: no specs exist yet -->

## Impact

- Code:
  - `SgProcessor`: pipeline split into save and notify decisions; status-aware change detection; timestamp rules
  - `SgMapper` / `SgEventEntry`
  - `stopgame.md.ftl`: new 🔁 header; unreachable FINISHED branch removed (FINISHED never notifies); `<пусто>` replaced with a single `<i>пусто</i>` marker
- Database: new Mongock migration `V003*` on collection `site_stopgame_events`
- Tests: integration scenarios with HTML fixtures and JSON expectations for each transition and for the migration. Reuse the work from branch `integration-tests-and-bug-fixes`:
  - Take as-is:
    - the Testcontainers upgrade to `1.21.4`, which Docker 29 / OrbStack requires
    - the fake Telegram bot properties in `AbstractIntegrationTest`
  - Existing `SgIntegrationTest` scenarios to keep, with expectations updated to the new rules:
    - `NO_EVENTS`, `NEW_SCHEDULED_EVENTS`, `NEW_LIVE_AND_SCHEDULED_EVENTS`: unchanged
    - `CANCELED_EVENT`: `endedAt` stays empty
    - `CHANGED_EVENTS`: the message contains `<i>пусто</i>` instead of `<пусто>`
    - `NO_CHANGES`: the LIVE event in the seed keeps its scheduled `date`
  - `B_FINISHED` (LIVE event disappears): keep the "no notification" expectation, but seed the LIVE event with its `date`, and fix the scenario description (it says SCHEDULED).
  - Drop the unused `b_finished*` fixtures, which expect a "✅ Событие завершено" message that this change rules out. Drop the `SgMapper` date-preserving spot fix as well; it is superseded.
  - New scenario: several processing runs against the same page (event goes live → stays live → disappears → returns) must save and notify only on the first run of each transition. Single-run scenarios cannot catch the regressions described above.
  - Cover the remaining TODO cases listed in `SgIntegrationTest` where they touch the lifecycle: returning CANCELED and FINISHED events, and mass changes.
- Release: must ship before `v1.2.0` reaches production; production currently holds only `v1.1.0` data
