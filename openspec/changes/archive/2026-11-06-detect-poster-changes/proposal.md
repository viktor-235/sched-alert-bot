# Proposal

## Why

The bot decides that the poster changed by comparing `imageUrl` strings. Stopgame changes the URL without changing the image, so subscribers get "🆙 Обновление события" whose only content is "🖼️ Новый постер", while the picture is the same. Production logs for 2026-10-18 … 2026-10-25 (v1.2.0) show 15 notifications with `changed=[imageUrl]` only. Most of them come about 30 minutes before the stream starts (at hh:31 / hh:32), for example:

```
2026-10-24T11:31 Event 2501 'Hunt: Showdown…': SCHEDULED -> SCHEDULED, changed=[imageUrl], save=true, notification=UPDATE
```

The poster URL has the form `https://images.stopgame.ru/streams/<yyyy>/<mm>/<dd>/c583x325/<signature>/<file>.jpg?<unix time>`. On 2026-10-25 the URLs on the page were compared with the `Last-Modified` header of the files:

| Event | `?<unix time>` in the URL | `Last-Modified` of the file |
|---|---|---|
| 2488 | 2026-10-25 14:30Z (30 min before the stream) | 2026-10-20 19:10Z |
| 2502 | 2026-10-25 11:30Z (30 min before the stream) | 2026-10-23 19:08Z |
| 2478 | 2026-10-24 20:24Z | 2026-10-24 20:24Z |

So 30 minutes before a stream the site bumps only the query string, and the file stays the same. A real upload creates a new file, with a new path. For 2502 the log has an `imageUrl` change at 2026-10-23 21:09 CEST, which matches its file's `Last-Modified` time. For 2478 the poster was uploaded at 22:24 CEST, three hours after the event appeared. Both are real changes, and subscribers should keep getting them. The server ignores the query string: the same bytes come back with any value or with none.

## What Changes

- Two poster URLs are compared without their query string. If only `?<unix time>` differs, `imageUrl` does not count as a changed field.
- The new URL, query included, is still saved, so messages and the calendar use the current URL. Saving it does not send a notification.
- A changed path, a poster added to an event that had none, or a poster removed still counts as a change, as today. The card shows "🖼️ Новый постер".
- Notifications sent regardless of field changes are not affected: "🆕 Новое событие", "🔴 В эфире" on entering LIVE, "❌ Отмена события", "🔁 Снова в расписании".
- Affected transitions, for all subscribed Telegram users. Only a query-only `imageUrl` change is now silent:
  - SCHEDULED → SCHEDULED: no "🆙 Обновление события".
  - LIVE → LIVE: no repeated "🔴 В эфире".
  - LIVE → SCHEDULED: no "🆙 Обновление события". The status change is saved silently, as it already is when no field changes.
- No event status or status transition changes.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `event-notifications`: an `imageUrl` change counts as a field change only if the URL differs without its query string. Affects "Update notification", "Live notification" and "Card content" ("Новый постер").
- `event-lifecycle`: "Save only on real change". A query-only `imageUrl` change is still saved, so the stored URL stays current, but it is not a field change.

## Impact

- Code: `SgProcessor`, or the comparison of `imageUrl` in `CompareService`, ignores the query string of image URLs and keeps the change for saving only.
- Database, config, network: no change.
- Tests: `SgProcessor` cases for a query-only change, a new path, a poster added and a poster removed, in SCHEDULED and LIVE. An integration scenario with HTML fixtures that differ only in the poster query.
- Specs: Mermaid diagram in `event-notifications` ("if changed" conditions).

## Assumptions

- A real poster upload always produces a new path. A file replaced in place at the same path, with only the query bumped, would be missed. This has not been observed.
- When an event is rescheduled, the date folder in the path can change together with the date (event 2470 on 2026-10-24). The card then also shows "🖼️ Новый постер" next to the date change. This is acceptable: the notification is sent because of the date anyway.
