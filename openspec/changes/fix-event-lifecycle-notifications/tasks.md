# Tasks

## 1. Test baseline from `integration-tests-and-bug-fixes`

- [x] 1.1 Port the fixture rename `a-b_live-c-msg.json` → `a_live-b-c-msg.json` and `a-b_live-c-seed.json` → `a_live-b-c-seed.json` with `git mv`, and update the paths in `SgIntegrationTest`. Do not port the `SgMapper` / `SgProcessor` date spot fix, the `b_finished*` fixtures or `a_finished-b-c-db-expected.json` from that branch. Verify: `./gradlew test --tests '*SgIntegrationTest'` passes unchanged.
- [x] 1.2 Port `src/test/resources/web/stopgame/events/b-c.html` (page without event `a`). Verify: the file parses to events `b` and `c` only, with a one-off assertion in `SgScraperTest`.

## 2. Status resolution

- [x] 2.1 In `SgProcessor.updateStatus`, decide a disappeared SCHEDULED event by its stored `date` and `Instant.now(clock)`: CANCELED if `date >= now`, FINISHED if `date < now` or `date == null` (design decision 3). Verify: new `SgProcessorTest` cases for date after now, equal to now, before now, and null.
- [x] 2.2 Make FINISHED → on site resolve like CANCELED → on site (SCHEDULED, or LIVE if live). Make LIVE → on site and not live resolve to SCHEDULED. Verify: `SgProcessorTest` cases for FINISHED returning live and not live, and for LIVE shown as not live; existing `updateStatus_*` tests still pass.

## 3. Stable comparison

- [x] 3.1 Annotate `SgEventWeb.nowLive` with `@DiffIgnore` so that live state is not a field diff. Verify: a `CompareServiceTest` (or `SgProcessorTest`) case shows that two events differing only in `nowLive` produce no diffs.
- [x] 3.2 Before comparing, substitute `db.date` into the web snapshot when the event is on the site with `web.date == null` (design decision 2), so compare and `updateFromWeb` both keep the stored date. Verify: a `SgProcessorTest` case for a LIVE event with a stored date shows no date diff and the saved entry keeps the date; a new live event still has `date == null`.

## 4. Save / notify pipeline and template

- [x] 4.1 Add a `NotificationType` enum (`NEW`, `UPDATE`, `LIVE`, `CANCELED`, `RETURNED`, `NONE`) with a pure `of(prevStatus, newStatus, hasFieldDiffs)` that implements `specs/event-notifications`. Verify: a parameterized unit test covers every requirement scenario, including LIVE → SCHEDULED with and without changes, SCHEDULED → FINISHED, and LIVE → FINISHED returning `NONE`.
- [x] 4.2 Replace `EventSnapshot.changed` with `shouldSave` (new event, status changed, or field diffs) and `notification`. Restructure `process()` per design decision 1: save first, then send only when `notification != NONE`. Verify: `SgProcessorTest` cases show that LIVE → FINISHED saves without sending, an unchanged LIVE event neither saves nor sends, and FINISHED → SCHEDULED with no field changes saves and sends.
- [x] 4.3 Apply the timing rules in `saveChanges` (design decision 4): entering LIVE sets `startedAt = now` and clears `endedAt`; LIVE → FINISHED sets `endedAt = now`; nothing else touches them, so the CANCELED branch is removed. Verify: `SgProcessorTest` cases for each rule in `specs/event-lifecycle` "Stream timing".
- [x] 4.4 Pass `notification` into the template context. In `stopgame.md.ftl`, choose the header and field layout by it, add the `RETURNED` header "🔁 Снова в расписании", remove the unreachable FINISHED handling, and replace every `"<пусто>"` with a single `<#assign EMPTY = "<i>пусто</i>">`. Verify: `SgTemplateServiceTest` is updated to the new context and gains cases for `RETURNED` and for an emptied description (`ℹ️ ... → <i>пусто</i>`); no output contains `<пусто>`.
- [x] 4.5 Update the integration expectations to the new rules:
  - `CANCELED_EVENT`: expected DB has `endedAt` empty
  - `CHANGED_EVENTS`: message contains `<i>пусто</i>`
  - `NO_CHANGES`: the live event in `a_live-b-c-seed.json` gets its scheduled `date`, and the seed is used as the expected DB
  - `NEW_SCHEDULED_EVENTS`, `NEW_LIVE_AND_SCHEDULED_EVENTS`, `NO_EVENTS`: unchanged
  Verify: `./gradlew test --tests '*SgIntegrationTest'` passes.
- [x] 4.6 Add the `B_FINISHED` scenario with a correct description ("a LIVE event disappears from the site"): seed `a_live-b-c-seed.json` (live event with a `date`), page `b-c.html`, a new `a_finished-b-c-db-expected.json` (FINISHED, date kept, `startedAt` from the seed, `endedAt = now`), no messages. Verify: the scenario passes.

## 5. Lifecycle integration scenarios

- [x] 5.1 Add a multi-run integration test (design decision 8) with a mutable test clock: `a-b-c` ×2 → `a_live-b-c` ×2 → `b-c` ×2 → `a-b-c` ×2. After each call, assert the DB state and only the messages sent during that call. Expected: 3× 🆕 / nothing / 🔴 / nothing / nothing / nothing / 🔁 / nothing; `startedAt` is set once and `endedAt` on the `b-c` step. Verify: the test passes.
- [x] 5.2 Add parameterized scenarios for returning events: CANCELED event back, not live, unchanged (🔁 card); FINISHED event back with a new date (🔁 with date diff); CANCELED event back live (🔴). Add seeds and expectations following the fixtures README. Verify: the scenarios pass.
- [x] 5.3 Add a scenario for a SCHEDULED event with a past `date` missing from the page: FINISHED, `startedAt` / `endedAt` empty, no message. Verify: the scenario passes.
- [x] 5.4 Add a mass-change scenario: in one run, one event changed, one is new, one canceled, and one LIVE event disappeared. Each gets exactly its own message, and the LIVE one gets none. Verify: the scenario passes.
- [x] 5.5 Add a scenario with two registered users that checks each message goes to both chats. Verify: the scenario passes.
- [x] 5.6 Update the TODO list in `SgIntegrationTest`: remove the cases now covered (3, 4, 5, 6, 7, 8) and fix case 4, which describes the ruled-out "message about finishing". Verify: the remaining TODOs only list uncovered cases.

## 6. Duplicate ids

- [x] 6.1 In `getData`, collect web events with a merge function that keeps the first occurrence and logs a warning, into a `LinkedHashMap` (design decision 6). Verify: a `SgProcessorTest` case where the parser returns two events with the same id processes the first one and the other events, and does not throw.

## 7. Legacy migration

- [x] 7.1 Add `migration/V003MigrateSgEventStatus` (order `003`, with `MongoTemplate` and `Clock`) implementing the execution and rollback in design decision 7. Verify: the class compiles and the application context starts in `SgIntegrationTest`.
- [x] 7.2 Add a migration integration test: insert raw `v1.1.0` documents (with `nowLive` true / false-future / false-past / false-no-date, and no `status`), run the execution with a fixed clock, and assert status, removed `nowLive`, unchanged `date`, and empty `startedAt` / `endedAt`. Run the rollback and assert that `nowLive` is restored and `status` removed. Re-running the execution changes nothing. Verify: the test passes.

## 8. Final check

- [x] 8.1 Run `./gradlew test` and verify the whole suite passes.
