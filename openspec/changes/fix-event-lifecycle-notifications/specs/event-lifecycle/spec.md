# Spec Delta

## Purpose

Defines how a scraped event's status, scheduled date and stream timing are derived from what the scraper observes on each run and how they are stored, so that the stored state is stable across runs and archived events stay queryable.

## ADDED Requirements

### Requirement: Status derived from site observation
Each processing run SHALL derive an event's new status from its stored status and whether the event is on the site and live: on the site and live → LIVE; on the site and not live → SCHEDULED; not on the site → FINISHED or CANCELED as defined by the disappearance requirements. An event that is not stored yet SHALL get LIVE or SCHEDULED from the site.

#### Scenario: New event not live
- **WHEN** an event appears on the site, is not live, and is not stored
- **THEN** it is stored with status SCHEDULED

#### Scenario: New event already live
- **WHEN** an event appears on the site already live and is not stored
- **THEN** it is stored with status LIVE

#### Scenario: Scheduled event goes live
- **WHEN** a SCHEDULED event is on the site and is live
- **THEN** its status becomes LIVE

#### Scenario: Live event shown as not live again
- **WHEN** a LIVE event is on the site and is no longer live
- **THEN** its status becomes SCHEDULED

#### Scenario: Canceled event returns to the site
- **WHEN** a CANCELED event is on the site again and is not live
- **THEN** its status becomes SCHEDULED

#### Scenario: Canceled event returns live
- **WHEN** a CANCELED event is on the site again and is live
- **THEN** its status becomes LIVE

#### Scenario: Finished event returns to the site
- **WHEN** a FINISHED event is on the site again and is not live
- **THEN** its status becomes SCHEDULED

#### Scenario: Finished event returns live
- **WHEN** a FINISHED event is on the site again and is live
- **THEN** its status becomes LIVE

### Requirement: Live event disappears
A LIVE event that is no longer on the site SHALL become FINISHED.

#### Scenario: Stream ends
- **WHEN** a LIVE event is no longer on the site
- **THEN** its status becomes FINISHED

### Requirement: Scheduled event disappears
A SCHEDULED event that is no longer on the site SHALL become CANCELED if its stored `date` is at or after the current time, and FINISHED if its stored `date` is before the current time or is empty. The current time SHALL be taken from the application clock.

#### Scenario: Disappears before its date
- **WHEN** a SCHEDULED event with `date` after now is no longer on the site
- **THEN** its status becomes CANCELED

#### Scenario: Disappears exactly at its date
- **WHEN** a SCHEDULED event with `date` equal to now is no longer on the site
- **THEN** its status becomes CANCELED

#### Scenario: Disappears after its date
- **WHEN** a SCHEDULED event with `date` before now is no longer on the site
- **THEN** its status becomes FINISHED

#### Scenario: Disappears without a date
- **WHEN** a SCHEDULED event with an empty `date` is no longer on the site
- **THEN** its status becomes FINISHED

### Requirement: Ended events stay ended while absent
A CANCELED or FINISHED event that is not on the site SHALL keep its status and SHALL NOT be saved again.

#### Scenario: Canceled event still absent
- **WHEN** a CANCELED event is not on the site on a later run
- **THEN** it stays CANCELED and is not saved

#### Scenario: Finished event still absent
- **WHEN** a FINISHED event is not on the site on a later run
- **THEN** it stays FINISHED and is not saved

### Requirement: Save only on real change
An event SHALL be saved when its status changes or at least one of its fields changes, including when the status changes and no field does. An event whose status and fields are both unchanged SHALL NOT be saved.

#### Scenario: Status changes, fields do not
- **WHEN** a SCHEDULED event goes live and none of its fields changed
- **THEN** the event is saved with status LIVE

#### Scenario: Fields change, status does not
- **WHEN** a SCHEDULED event is on the site with a changed description
- **THEN** the event is saved with the new description and status SCHEDULED

#### Scenario: Nothing changes
- **WHEN** a SCHEDULED or LIVE event is on the site with the same status and fields as stored
- **THEN** the event is not saved

### Requirement: Idempotent processing
Running processing again against an unchanged site SHALL save nothing, in every status.

#### Scenario: Live event across several runs
- **WHEN** processing runs several times while an event stays live on the site
- **THEN** only the first run saves the event, and `startedAt` keeps the value set by that first run

#### Scenario: Full lifecycle across several runs
- **WHEN** processing runs twice for each of these site states in turn: event scheduled, event live, event gone, event scheduled again
- **THEN** the event is saved only on the first run of each state

### Requirement: Scheduled date kept from the site
The stored `date` SHALL be the last non-empty date the site showed for the event. An empty date from the site, as shown while the event is live, SHALL NOT overwrite the stored `date` and SHALL NOT count as a field change. The stored `date` SHALL NOT be cleared when the event becomes FINISHED or CANCELED.

#### Scenario: Date kept while live
- **WHEN** a SCHEDULED event with a stored `date` goes live and the site shows no date
- **THEN** the stored `date` is unchanged and no date change is reported

#### Scenario: Date kept after the stream
- **WHEN** a LIVE event with a stored `date` becomes FINISHED
- **THEN** the stored `date` is unchanged

#### Scenario: Date kept after cancellation
- **WHEN** a SCHEDULED event becomes CANCELED
- **THEN** the stored `date` is unchanged

#### Scenario: Rescheduled event
- **WHEN** a SCHEDULED event is on the site with a different non-empty date
- **THEN** the stored `date` is replaced with the new date

#### Scenario: Event first seen live
- **WHEN** an event is first seen already live
- **THEN** it is stored with an empty `date`

### Requirement: Stream timing
`startedAt` SHALL be set to now and `endedAt` cleared when an event enters LIVE from any other state or as a new event. `endedAt` SHALL be set to now on LIVE → FINISHED. All other transitions SHALL leave `startedAt` and `endedAt` unchanged.

#### Scenario: Scheduled event goes live
- **WHEN** a SCHEDULED event goes live
- **THEN** `startedAt` is now and `endedAt` is empty

#### Scenario: New event already live
- **WHEN** a new event is first seen live
- **THEN** `startedAt` is now and `endedAt` is empty

#### Scenario: Finished event goes live again
- **WHEN** a FINISHED event with `startedAt` and `endedAt` set returns to the site live
- **THEN** `startedAt` is now and `endedAt` is empty

#### Scenario: Stream ends
- **WHEN** a LIVE event becomes FINISHED
- **THEN** `endedAt` is now and `startedAt` is unchanged

#### Scenario: Cancellation
- **WHEN** a SCHEDULED event becomes CANCELED
- **THEN** `startedAt` and `endedAt` are unchanged

#### Scenario: Scheduled event finishes unseen
- **WHEN** a SCHEDULED event becomes FINISHED because its `date` passed
- **THEN** `startedAt` and `endedAt` stay empty

### Requirement: Duplicate events on a page
If the scraped page contains the same event id more than once, processing SHALL use the first occurrence, log a warning, and continue with the other events.

#### Scenario: Duplicate id on the page
- **WHEN** the page contains two events with the same id
- **THEN** the first one is processed, a warning is logged, and all other events on the page are processed normally

### Requirement: Legacy record migration
On startup, a one-time migration SHALL convert stored events that have `nowLive` and no `status`: `nowLive = true` → FINISHED; `nowLive = false` with `date` at or after now → SCHEDULED; `nowLive = false` with `date` before now or empty → FINISHED. It SHALL remove `nowLive`, leave `startedAt`, `endedAt` and `date` as stored, and provide a rollback.

#### Scenario: Legacy live event
- **WHEN** a stored event has `nowLive = true`
- **THEN** after migration it has status FINISHED, no `nowLive`, and empty `startedAt` and `endedAt`

#### Scenario: Legacy future event
- **WHEN** a stored event has `nowLive = false` and `date` after now
- **THEN** after migration it has status SCHEDULED and no `nowLive`

#### Scenario: Legacy past event
- **WHEN** a stored event has `nowLive = false` and `date` before now
- **THEN** after migration it has status FINISHED and no `nowLive`

#### Scenario: Legacy event without a date
- **WHEN** a stored event has `nowLive = false` and no `date`
- **THEN** after migration it has status FINISHED and no `nowLive`

#### Scenario: Legacy event that was live keeps its empty date
- **WHEN** a stored event has `nowLive = true` and no `date`
- **THEN** after migration its `date` is still empty

#### Scenario: Migration rollback
- **WHEN** the migration is rolled back
- **THEN** every migrated event has `nowLive` again (true for LIVE, false otherwise) and no `status`
