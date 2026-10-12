# Design

## Context

See proposal.md (Why) for the motivation; requirements are in `specs/event-lifecycle` and `specs/event-notifications`.

The current `SgProcessor.process()` is one stream: `compare → updateStatus → filter(changed) → generateMsg → send → save`. Three things about it shape this design:

- `changed` is set only by `compare`, which diffs `mapper.toWeb(db)` against the scraped `SgEventWeb`. `toWeb` derives `nowLive` from `status == LIVE`, so SCHEDULED → LIVE was "detected" only because `nowLive` differs. Nothing detects a status change without a matching field (for example FINISHED → SCHEDULED).
- While an event is live the scraper returns `date = null`. `compare` reports this as a date change, and `updateFromWeb` copies the null into the entry. The spot fix on `integration-tests-and-bug-fixes` kept the date at save time but not at compare time. The diff then reappeared on every run, so "🔴" was re-sent and `startedAt` was overwritten each time.
- The FreeMarker template chooses the header from `newStatus` and `newEvent` only. It cannot tell "returned after cancel" from "updated", because it never sees the previous status.

## Goals / Non-Goals

**Goals:**
- Keep the lifecycle rules (new status, save or not, which notification, timing) in Java, as small pure functions covered by unit tests. The template only renders text.
- Normalize the "live means no date" quirk in one place, before any comparison or mapping.
- Keep the shape reusable for future sources. The rules depend only on stored status, presence on the site, live flag and field diffs, not on stopgame specifics.

**Non-Goals:**
- Extracting a shared, source-agnostic processor base class. The rules stay in `SgProcessor` until a second source exists. Only the pure decision logic is kept free of `Sg` types where that costs nothing.
- Telling broken markup from an empty page (change `guard-against-broken-scraping`).
- HTML-escaping scraped values (out of scope per proposal).
- Detecting list field changes in `CompareService` (change `detect-list-field-changes`).

## Decisions

### 1. Separate the transition, save and notify decisions

The pipeline becomes:

```
getData (full outer join)
  -> normalize web snapshot          (decision 2)
  -> compare fields                  (fieldDiffs, nowLive excluded)
  -> resolve newStatus               (prevStatus, onSite, live, db.date, clock)
  -> shouldSave = isNew || newStatus != prevStatus || !fieldDiffs.isEmpty()
  -> notification = NotificationType.of(prevStatus, newStatus, fieldDiffs)  // may be NONE
  -> if shouldSave: apply timing, save
  -> if notification != NONE: render + send
```

`EventSnapshot.changed` is replaced by `shouldSave` and `notification`. `NotificationType` is an enum: `NEW`, `UPDATE`, `LIVE`, `CANCELED`, `RETURNED`, `NONE`. A static method maps a transition to it, following the tables in `specs/event-notifications`. This method and the status resolution (`updateStatus`, already unit-tested in `SgProcessorTest`) are the units tested exhaustively.

*Alternative:* keep one `changed` flag and add status-change detection to it. This was rejected because it is exactly the coupling that caused the regressions. LIVE → FINISHED and LIVE → SCHEDULED without changes must be saved but not notified, so one flag cannot express both decisions.

**Order of save and send.** Save first, then send. If sending fails after the save, subscribers miss one message. If we sent first and the save then failed, every following run would send the message again. The current code sends first; this change swaps the order.

### 2. Normalize the web snapshot instead of special-casing the mapper

Before comparing, if the event is on the site with `web.date == null` and the stored entry has a date, the snapshot's `date` is set to `db.date`. Compare and `updateFromWeb` then both see a stable date. No mapper `@Context` or null-handling strategy is needed. The `SgMapper` spot fix is not ported.

`nowLive` is excluded from the field diff with Javers `@DiffIgnore` on `SgEventWeb.nowLive`, the same annotation `SgEventEntry` already uses. "Live" is a status concern and is handled by decision 1. If it stayed a diffed field, every SCHEDULED → LIVE would count as a field change, and LIVE → SCHEDULED without changes would wrongly send "🆙".

A new event first seen live has no stored date, so nothing is substituted and its `date` stays empty, as the spec requires.

### 3. Disappearance uses the stored date and the clock

When the event is not on the site: LIVE → FINISHED; SCHEDULED → CANCELED if `db.date != null && !db.date.isBefore(now)`, otherwise FINISHED. Here `now = Instant.now(clock)`. The boundary matches the migration (`>= now` is still scheduled).

A side effect: an event that went LIVE → SCHEDULED (accepted limitation in the proposal) and later disappears usually has a past date by then, so it ends as FINISHED instead of a false "❌".

### 4. Timing applied from the transition

When saving:
- `prev != LIVE && new == LIVE`: `startedAt = now`, `endedAt = null`. This includes new events.
- `prev == LIVE && new == FINISHED`: `endedAt = now`.
- Otherwise neither field is touched.

The current `CANCELED → endedAt = now` branch is removed.

### 5. The template renders a type, the processor picks it

The processor puts `notification` (the enum name) into the template context. `addTitle` switches on it and keeps all Russian header texts in the template, including the new "🔁 Снова в расписании" for `RETURNED`. The unreachable FINISHED handling in `addDate` is removed.

Field rendering still depends on the event being live or canceled:
- the date line is hidden for `LIVE`
- `CANCELED` uses the stored (old) values

These checks switch from `status` to `notification`, so each header maps one-to-one to a layout.

The empty marker is defined once as `<#assign EMPTY = "<i>пусто</i>">` and used in all seven places that currently write `"<пусто>"`.

*Alternative:* pass `prevStatus` and keep the decision logic in FreeMarker. Rejected: the rules would be untestable without rendering, and they would be duplicated for every future source template.

### 6. Duplicate ids

`Collectors.toMap(SgEventWeb::getId, identity(), (first, dup) -> { log.warn(...); return first; }, LinkedHashMap::new)`. The `LinkedHashMap` keeps page order, so the messages within a run come out in a predictable order.

### 7. Migration `V003MigrateSgEventStatus`

`@ChangeUnit(id = "migrateSgEventNowLiveToStatus", order = "003")`, with `MongoTemplate` and `Clock` injected; Mongock resolves Spring beans. All updates use `updateMulti` and are restricted to `{nowLive: {$exists: true}, status: {$exists: false}}`, so they are safe to re-run. In order:
1. `nowLive: true` → `status = FINISHED`.
2. `nowLive: false, date >= now` → `status = SCHEDULED`.
3. All remaining matching documents (`date < now` or no `date`) → `status = FINISHED`.
4. `$unset nowLive` on documents that now have `status`.

Rollback: `nowLive = (status == LIVE)`, then `$unset status`. This loses information: legacy `nowLive = true` records come back as `false`, because they were turned into FINISHED. That is acceptable: rollback is an emergency path, and those streams ended long ago.

The `null` status branch in `updateStatus` (logged as a warning, treated as CANCELED) stays only as a defensive fallback. After the migration no stored event lacks a status.

### 8. Tests

- **Unit (`SgProcessorTest`)**: status resolution for every row of the lifecycle spec, including the `date == now` boundary and `date == null`. `NotificationType.of` for every row of the notification tables. Timing rules.
- **Integration (`SgIntegrationTest`, parameterized)**: keep the existing scenarios as listed in the proposal. Port from `integration-tests-and-bug-fixes`:
  - the fixture rename `a-b_live-c-* → a_live-b-c-*` (the live event is `a`, per the fixtures README)
  - `b-c.html`, used for `B_FINISHED`
  - the `B_FINISHED` scenario itself
  For `NO_CHANGES`, the seed gets a `date` on the live event, and the seed file itself serves as the expected DB state. Nothing changes, so the DB equals the seed. `NEW_LIVE_AND_SCHEDULED_EVENTS` keeps its own expectation with an empty `date`.
- **Integration, multi-run**: a separate, non-parameterized test that calls `process()` several times and swaps the stubbed document and a mutable test clock between calls: `a-b-c` ×2 → `a_live-b-c` ×2 → `b-c` ×2 → `a-b-c` ×2. After each call it asserts the DB and the messages sent during that call. Fresh mock invocations are counted per step, using `clearInvocations`.
- **Migration**: an integration test that inserts raw `v1.1.0`-shaped documents with `MongoTemplate` (with `nowLive` and without `status`), runs the change unit's execution and rollback methods directly with a fixed clock, and asserts the resulting documents.

## Risks / Trade-offs

- [Save-then-send loses a message if Telegram fails after the save] → Accepted. A lost message is better than one repeated every 10 minutes. The error is logged; alerting is the README TODO.
- [Rollback of V003 is lossy for legacy live records] → Documented in decision 7. Production holds `v1.1.0` data only, so only records that were live at upgrade time are affected.
- [Template layout keyed by notification type instead of status] → Every existing message fixture is regenerated or checked against the new rendering. Header texts, except the new 🔁, must match the existing fixtures byte for byte, which the integration tests enforce.
- [First run after deploy sends late "❌" for stale scheduled events and a repeated "🔴" for a stream live at deploy time] → Accepted in the proposal. The migration already turns past-dated legacy events into FINISHED, so a late "❌" is sent only for a future-dated event that is missing from the site, which is a real cancellation.
- [Participants-only changes are lost; the cancellation card has no participants] → Accepted for this change, pre-existing behavior; fixed separately in `detect-list-field-changes`.

## Migration Plan

1. Deploy `v1.2.0`. Mongock runs `V003` on startup, before the scheduler fires.
2. The first processing run applies the new rules to the migrated data.
3. Rollback: redeploy `v1.1.0` after running the Mongock rollback for `003` (Mongock CLI or a manual `undo`). `v1.1.0` ignores `startedAt` and `endedAt`.
