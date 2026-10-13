# Tasks

## 1. Detect list changes

- [x] 1.1 In `CompareService`, map every Javers `PropertyChange` to `FieldDiff(propertyName, left, right)` instead of only `ValueChange` (design decision 1), with a comment on why nested object fields are not expected. Verify: new `CompareServiceTest` cases: a list change `[A, B] → [A, C]` and `[A, B] → []` gives one `participants` diff with the old and new lists; `null` vs empty list gives no diff; the existing value-change cases still pass.

## 2. Template

- [x] 2.1 In `stopgame.md.ftl` `addList`, render an empty or missing old list as `EMPTY` (design decision 3). Verify: `SgTemplateServiceTest` cases for participants added (`🧑‍🧒‍🧒 <i>пусто</i> → Андрей Маковеев`) and participants removed (old names followed by ` → <i>пусто</i>`).

## 3. Processing

- [x] 3.1 Verify the processor needs no change. Add `SgProcessorTest` cases: a SCHEDULED event whose only change is participants is saved and notified with `UPDATE`; a LIVE event whose only change is participants is saved and notified with `LIVE`; an event stored with `null` participants and shown with none is neither saved nor notified.

## 4. Integration scenarios

- [x] 4.1 Update `a_canceled-c_canceled-msg.json`: the cancellation card for event `a` gains `🧑‍🧒‍🧒 Ян Грибович, Иван Лоев`; event `c`, which has no participants, stays without the line. Verify: `CANCELED_EVENT` passes.
- [x] 4.2 Add a `PARTICIPANTS_CHANGED` scenario: seed `a-b_old_participants-c-seed.json` (event `b` stored with participants `Андрей Маковеев` only), page `a-b-c.html`, expected DB with `b` holding both participants and version 1, and one message `🆙 Обновление события` with `🧑‍🧒‍🧒 Андрей Маковеев → Андрей Маковеев, Дмитрий Карнов`. Add the `_old_participants` suffix to the fixtures README. Verify: the scenario passes.
- [x] 4.3 Check the other integration expectations still pass unchanged (the mass-change and return scenarios include participants on both sides, so they must not show a diff). Verify: `./gradlew test --tests '*SgIntegrationTest'` passes.

## 5. Final check

- [x] 5.1 Run `./gradlew test` and verify the whole suite passes.
