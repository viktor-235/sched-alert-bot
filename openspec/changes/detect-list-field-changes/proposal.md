# Proposal

## Why

`CompareService` keeps only Javers `ValueChange` entries, but Javers reports a changed list property (`participants`) as `ListChange`, so every participants change is dropped. As a result, a participants-only change is neither saved nor notified, a participants change together with other fields is not shown as old → new, and the "❌ Отмена события" card has no participants. Change `fix-event-lifecycle-notifications` recorded this as a known limitation and removed the affected requirements from its specs; this change restores them.

Verified with a probe test against the current code: `[A, B] → [A, C]` and `[A, B] → []` both produce an empty diff from `CompareService`, while Javers itself returns a `ListChange`. When an event disappears, Javers returns two `TerminalValueChange` entries and a `ListChange`; only the first two reach the processor.

## What Changes

- `CompareService` turns every Javers property change (`PropertyChange`: value and container changes) into a `FieldDiff` with the old and new value, so a changed `participants` list becomes a field diff.
- A participants-only change on a SCHEDULED or LIVE event counts as a field change: the event is saved, and subscribers get "🆙 Обновление события" (SCHEDULED) or "🔴 В эфире" (LIVE) showing the participants as old → new.
- The "❌ Отмена события" card for a SCHEDULED event that becomes CANCELED lists the stored participants.
- An empty side of a participants change is rendered as `<i>пусто</i>`, including an empty old list (today `[]` renders as an empty string).
- A stored `null` participants list and an empty list from the site are not a change.
- Affected statuses: SCHEDULED and LIVE events on the site (update and live notifications), SCHEDULED → CANCELED (cancellation card), CANCELED / FINISHED returning to the site (the 🔁 card shows a participants diff). All subscribed Telegram users are affected equally.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `event-lifecycle`: "Save only on real change" covers a participants-only change.
- `event-notifications`: "Update notification" counts a participants change as a field change; "Cancellation notification" lists the stored participants; "Diff rendering" covers participants changes and an empty old list.

## Impact

- Code:
  - `compare/CompareService`: map `PropertyChange` instead of `ValueChange` only
  - `templates/stopgame.md.ftl`: `addList` renders an empty old list as `EMPTY`
- Tests:
  - `CompareServiceTest`: list changes, `null` vs empty list
  - `SgProcessorTest`: participants-only change is saved and notified
  - `SgTemplateServiceTest`: empty old list, participants removed
  - `SgIntegrationTest`: the cancellation message fixture `a_canceled-c_canceled-msg.json` gains the participants line; a new scenario for a participants-only change
