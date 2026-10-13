# Design

## Context

See proposal.md (Why). Three facts shape the approach:

- `CompareService.compare` builds a Javers diff and keeps only `ValueChange` entries. In Javers 7.7, both `ValueChange` and the container changes (`ListChange`, `SetChange`, ...) extend `PropertyChange`, which exposes `getPropertyName()`, `getLeft()` and `getRight()`. For a `ListChange` these return the whole old and new lists.
- A probe against the current code showed: a stored `null` list compared with an empty list produces no change; a new event produces `ListChange([] → [A, B])`; a disappearing event produces `ListChange([A, B] → [])`. So the cancellation card gets the stored participants as the old value with no extra code in the processor.
- `SgProcessor.decide` already counts any field diff when both the stored and the scraped event exist, so a participants diff makes the event save and notify with no processor change.

## Goals / Non-Goals

**Goals:**
- Detect list field changes in the shared `CompareService`, so that every source benefits.
- Keep the processor and `NotificationType` unchanged.

**Non-Goals:**
- Showing which participants were added or removed. The card shows the whole old list → the whole new list, the same as other fields.
- Detecting image changes differently; `imageUrl` is already a value field.

## Decisions

### 1. Map `PropertyChange` instead of `ValueChange`

`CompareService` filters `PropertyChange` and builds `FieldDiff(propertyName, left, right)`. Value changes keep their current behavior, because `ValueChange.getLeft()` / `getRight()` return the same values as before.

*Alternative:* handle `ListChange` as a separate branch. Rejected: two code paths for one concept, and other container types (sets, maps, arrays) would stay invisible to future sources.

### 2. List order is significant

Javers compares lists by index, so `[A, B] → [B, A]` is a change. That is kept: the card shows participants in the site's order, and a reorder on stopgame.ru is rare. If it ever becomes noisy, the list compare algorithm can be switched to "as set" in one place.

### 3. Template: an empty old list is `EMPTY`

`addList` renders the old side as `field.oldValue?join(", ")! EMPTY`. The default operator only covers a missing value, so an empty list renders as an empty string, and "added participants" would print `🧑‍🧒‍🧒  → A`. The old side switches to a `?has_content` check, like the new side already does.

## Risks / Trade-offs

- [A future source with a nested object field would get a `ReferenceChange`, whose left / right values are Javers ids, not readable values] → Not reachable today: `SgEventWeb` has only value and list fields. Documented in a comment on the filter.
- [The cancellation message fixture changes, because it gains the participants line] → Expected by the restored requirement; the integration test enforces the new text.
- [Reordered participants on the site send "🆙"] → Accepted, see decision 2.

## Migration Plan

No data migration: the stored document shape does not change.
