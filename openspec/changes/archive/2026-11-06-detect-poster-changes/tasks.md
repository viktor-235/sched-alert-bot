# Tasks

## 1. URL helper

- [x] 1.1 Add `UrlUtils.equalsIgnoringQuery(String, String)` to the shared `web` package (design decision 3). Verify: `UrlUtilsTest` cases: same path with different queries is equal; query vs no query is equal; different paths are not equal; a fragment is ignored; two `null`s are equal; `null` vs a URL is not equal.

## 2. Processing

- [x] 2.1 In `SgProcessor`, add the query-insensitive field set (`imageUrl`, design decision 2) and a `silentDiffs` list to `EventSnapshot`. `compare` moves a diff of such a field whose URLs are equal ignoring the query string from `fieldDiffs` to `silentDiffs` (design decision 1).
- [x] 2.2 `decide` saves when `silentDiffs` is not empty and both sides exist, and notifies based on `fieldDiffs` only. The log line gets `silent=[...]`. Verify: `SgProcessorTest` cases:
  - a SCHEDULED event with only a query change in `imageUrl` is saved with the new URL and not notified;
  - a LIVE event with only a query change is saved and not notified;
  - a SCHEDULED event with a new `imageUrl` path is saved and notified with `UPDATE`, and `imageUrl` is marked changed in the template context;
  - a SCHEDULED event with a date change and a query-only `imageUrl` change is notified with `UPDATE`, and `imageUrl` is not marked changed;
  - a poster added to an event that had none is notified with `UPDATE`.

## 3. Integration scenarios

- [x] 3.1 Add a `POSTER_URL_QUERY_CHANGED` scenario: seed `a-b_old_poster_query-c-seed.json` (event `b` stored with its image URL plus `?1700000000`), page `a-b-c.html`, expected DB with `b` holding the URL without query and version 1, and no messages.
- [x] 3.2 Add a `POSTER_REPLACED` scenario: seed `a-b_old_poster-c-seed.json` (event `b` stored with a different image path), page `a-b-c.html`, expected DB with `b` holding the new URL and version 1, and one message `🆙 Обновление события` with `🖼️ Новый постер` (`b_old_poster-msg.json`).
- [x] 3.3 Add the `_old_poster` / `_old_poster_query` suffixes to the fixtures README. Verify: `./gradlew test --tests '*SgIntegrationTest'` passes.

## 4. Final check

- [x] 4.1 No Mermaid diagram update needed: the "if changed" conditions in `event-notifications` keep their wording. Confirm after syncing specs.
- [x] 4.2 Run `./gradlew test` and verify the whole suite passes.
