# Design

## Context

See proposal.md (Why). The facts that shape the approach:

- `SgProcessor.compare` turns the stored and scraped event into `FieldDiff`s with the shared `CompareService` (Javers). `decide` saves on any field diff and passes `hasFieldDiffs` to `NotificationType.of`. `generateMsg` marks every diffed field as changed in the template, so an `imageUrl` diff prints "🖼️ Новый постер".
- `saveChanges` copies all scraped fields onto the stored entry with `SgMapper.updateFromWeb`. It does not look at the diffs.
- `CompareService` is source-agnostic and knows nothing about URLs.

## Goals / Non-Goals

**Goals:**
- A poster URL that differs only in its query string is saved, but it is not a field change. No notification, no "Новый постер".
- Keep `CompareService`, `NotificationType` and the template unchanged.

**Non-Goals:**
- Checking the image content (hash). Rejected in the proposal discussion: the evidence shows that the path alone tells a real upload from a cache-buster bump.
- Ignoring the query string for other fields or other sources. The rule is opt-in per field.

## Decisions

### 1. Split diffs into notifying and silent in the processor

`compare` keeps calling `CompareService`, then moves every diff of a query-insensitive field whose old and new URLs are equal without the query string from `fieldDiffs` to a new `silentDiffs` list on `EventSnapshot`. The rest of the pipeline reads them as follows:

- `decide`: `shouldSave` also becomes true when `silentDiffs` is not empty and both sides exist. `NotificationType.of` still gets `hasFieldDiffs`, which counts only `fieldDiffs`.
- `generateMsg`: unchanged. It uses `fieldDiffs`, so a silent `imageUrl` diff is not marked as changed.
- `saveChanges`: unchanged. `updateFromWeb` already copies the new URL.
- The run log line gets `silent=[...]` next to `changed=[...]`, so a silent save is visible in the logs.

*Alternative:* teach `CompareService` to ignore the query string, for example with a custom Javers comparator. Rejected: the event would then not be saved, so the stored URL would go stale, and URL knowledge would leak into the generic diff service.

*Alternative:* drop the diff and always save scraped data. Rejected: it changes when events are saved (`version`, `updatedAt`, the calendar export trigger) for every run.

### 2. Query-insensitive fields are a constant in `SgProcessor`

`Set.of(SgEventWeb.Fields.imageUrl)`. The rule describes how stopgame.ru serves images, so it belongs to the source. It is not a scraping selector, so it does not go to `application.yml`.

### 3. URL helper in the shared `web` package

`UrlUtils.equalsIgnoringQuery(String, String)` compares the parts before the first `?` or `#`. Two `null`s are equal, and `null` versus a URL is not. A plain string split is used rather than `java.net.URI`, so that unencoded characters in a file name cannot make parsing fail.

## Risks / Trade-offs

- [A poster replaced in place at the same path, with only the query bumped, is missed] → Not observed. A content check can be added as a separate change if it ever happens.
- [A rescheduled event can get a new date folder in the path, so the card shows "Новый постер" next to the date change] → Accepted: the notification is sent because of the date anyway.

## Migration Plan

No data migration: the stored document shape does not change.
