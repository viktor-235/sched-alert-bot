# calendar-export Specification

## Purpose

Exports a site's stored events as an iCalendar (`.ics`) file, so that the schedule can be imported into or subscribed to from a calendar app, with stable entries that update in place as events change.

## Requirements

### Requirement: Export is optional and per site
Each site SHALL have its own export settings: enabled flag, file path, default event duration, past period, stream URL and an optional time zone. Export SHALL be disabled by default. The time zone SHALL be unset by default. When disabled, the site's processing SHALL NOT create, change or delete any calendar file. The past period SHALL default to 3 years. Durations and periods SHALL use the application's standard duration and period formats (e.g. `120m`, `3y`).

#### Scenario: Export disabled by default
- **WHEN** a site has no export settings and a run saves events
- **THEN** no calendar file is created

#### Scenario: Export explicitly disabled
- **WHEN** export is disabled for a site, a calendar file exists at its path, and a run saves events
- **THEN** the existing file is left unchanged

#### Scenario: Default past period
- **WHEN** export is enabled without a past period
- **THEN** events whose start is up to 3 years before now are included

### Requirement: When the file is written
With export enabled, the calendar file SHALL be written after a run that saved at least one event, including an event that became FINISHED, and after a run when the file does not exist. A run that saved nothing SHALL leave an existing file untouched. Missing parent directories SHALL be created.

#### Scenario: Run saves an event
- **WHEN** export is enabled, the file exists, and a run saves a changed event
- **THEN** the file is regenerated

#### Scenario: Stream ends
- **WHEN** export is enabled and a run saves a LIVE event as FINISHED
- **THEN** the file is regenerated

#### Scenario: Run without saves
- **WHEN** export is enabled, the file exists, and a run saves nothing
- **THEN** the file is not modified

#### Scenario: File missing
- **WHEN** export is enabled, the file does not exist, and a run saves nothing
- **THEN** the file is written

#### Scenario: Directories missing
- **WHEN** export is enabled and the parent directories of the configured path do not exist
- **THEN** they are created and the file is written

### Requirement: File is a full snapshot replaced atomically
Each written file SHALL contain the site's complete current calendar built from stored events, not only the events of the last run, and SHALL replace the previous file. A reader SHALL never observe a partially written file.

#### Scenario: Unchanged stored events stay in the file
- **WHEN** a run saves one event and other events are stored but unchanged
- **THEN** the written file contains all included events, not only the saved one

#### Scenario: Reader during a write
- **WHEN** the file is being regenerated
- **THEN** a concurrent reader sees either the complete previous file or the complete new file

### Requirement: Included events
The file SHALL contain one entry per stored event of the site with status SCHEDULED, LIVE or FINISHED whose resolved start is not earlier than now minus the past period. There SHALL be no upper bound on the start. CANCELED events and events whose start cannot be resolved SHALL be excluded.

#### Scenario: Statuses included
- **WHEN** a site has one stored SCHEDULED, one LIVE and one FINISHED event, all within the past period
- **THEN** the file contains three entries

#### Scenario: Canceled event excluded
- **WHEN** an event in the file becomes CANCELED and the file is regenerated
- **THEN** the file no longer contains its entry

#### Scenario: Too old event excluded
- **WHEN** a FINISHED event started earlier than now minus the past period
- **THEN** the file does not contain it

#### Scenario: Far future event included
- **WHEN** a SCHEDULED event is dated two years ahead
- **THEN** the file contains it

#### Scenario: Undated event excluded
- **WHEN** a SCHEDULED event has no `date`
- **THEN** the file does not contain it

#### Scenario: No events
- **WHEN** export is enabled and no stored event is included
- **THEN** the file is a valid calendar with no entries

### Requirement: Valid iCalendar output
The file SHALL be RFC 5545 iCalendar content with one `VEVENT` per included event. `DTSTAMP` and `LAST-MODIFIED` SHALL always be in UTC.

#### Scenario: Revision times in UTC
- **WHEN** the time zone is `Europe/Moscow` and an event was last updated at 12:00Z
- **THEN** its entry has `DTSTAMP:...T120000Z` and `LAST-MODIFIED:...T120000Z`

### Requirement: Calendar name
Each site's export SHALL have an optional calendar name. When the name is set, the file SHALL contain it as the calendar properties `NAME` (RFC 7986) and `X-WR-CALNAME`. When the name is not set or blank, the file SHALL contain neither property.

#### Scenario: Name set
- **WHEN** the calendar name is `StopGame — Стримы`
- **THEN** the file contains `NAME:StopGame — Стримы` and `X-WR-CALNAME:StopGame — Стримы`

#### Scenario: Name not set
- **WHEN** no calendar name is configured
- **THEN** the file contains no `NAME` and no `X-WR-CALNAME`

### Requirement: Event time zone
Without a configured time zone, start and end times SHALL be written in UTC (`...Z` form) and the file SHALL contain no `VTIMEZONE`. With a configured time zone, start and end times SHALL be written as local times of that zone with a `TZID` parameter, and the file SHALL contain one `VTIMEZONE` for that zone. The time zone SHALL NOT change the instant an entry starts or ends.

#### Scenario: Times in UTC by default
- **WHEN** no time zone is configured and an event is scheduled at 20:00 Moscow time (UTC+3)
- **THEN** its entry starts at `...T170000Z` and the file contains no `VTIMEZONE`

#### Scenario: Times in the site's time zone
- **WHEN** the time zone is `Europe/Moscow` and an event is scheduled at 20:00 Moscow time
- **THEN** its entry starts at `DTSTART;TZID=Europe/Moscow:...T200000` and the file contains one `VTIMEZONE` with `TZID:Europe/Moscow`

### Requirement: Stable entry identity
Each entry's UID SHALL be derived from the site and the source event id, so that the same event has the same UID on every run and a changed event updates its existing calendar entry instead of creating a new one.

#### Scenario: UID stable across runs
- **WHEN** an event's date changes and the file is regenerated
- **THEN** its entry keeps the same UID

#### Scenario: Different sites
- **WHEN** two sites have events with the same source id
- **THEN** their entries have different UIDs

### Requirement: Entry revision fields
Each entry's `SEQUENCE` SHALL be the stored event's version, and its `LAST-MODIFIED` and `DTSTAMP` SHALL be the stored event's last update time. Regenerating the file when no included event changed SHALL produce byte-identical content.

#### Scenario: Changed event gets a higher sequence
- **WHEN** an event in the file is changed and saved, and the file is regenerated
- **THEN** its entry has a higher `SEQUENCE` and a newer `LAST-MODIFIED`

#### Scenario: Byte-identical regeneration
- **WHEN** the file is regenerated twice and no included event changed in between
- **THEN** both files are byte-identical

### Requirement: Entry time for scheduled and live events
For a SCHEDULED or LIVE event the entry SHALL start at the stored scheduled `date` and last the default event duration. The actual start time of a LIVE event SHALL NOT be used.

#### Scenario: Scheduled event
- **WHEN** a SCHEDULED event has `date` 20:00Z and the default duration is 120m
- **THEN** its entry runs from 20:00Z to 22:00Z

#### Scenario: Live event started late
- **WHEN** a LIVE event has `date` 20:00Z and actually started at 20:15Z
- **THEN** its entry still starts at 20:00Z and lasts the default duration

#### Scenario: Live event file does not change while live
- **WHEN** the file is regenerated several times while an event stays LIVE and unchanged
- **THEN** its entry is identical each time

### Requirement: Entry time for finished events
For a FINISHED event the entry SHALL start at the actual stream start and end at the actual stream end. If the actual start is unknown, the entry SHALL start at the scheduled `date`. If either the actual start or the actual end is unknown, the entry SHALL last the default event duration.

#### Scenario: Finished stream with known timing
- **WHEN** a FINISHED event has `date` 20:00Z, started at 20:10Z and ended at 23:05Z
- **THEN** its entry runs from 20:10Z to 23:05Z

#### Scenario: Finished event that was never live
- **WHEN** a FINISHED event has `date` 20:00Z and no actual start or end
- **THEN** its entry starts at 20:00Z and lasts the default duration

#### Scenario: Finished event without end
- **WHEN** a FINISHED event started at 20:10Z and has no actual end
- **THEN** its entry starts at 20:10Z and lasts the default duration

#### Scenario: Finished event without any start
- **WHEN** a FINISHED event has no `date` and no actual start
- **THEN** the file does not contain it

### Requirement: Entry content
Each entry's `SUMMARY` SHALL be the event name, its `URL` SHALL be the site's configured stream URL, its `DESCRIPTION` SHALL follow "Entry description", and the event image SHALL be attached as an RFC 7986 `IMAGE` property with the image URL. Empty values SHALL be omitted.

#### Scenario: Full entry
- **WHEN** an event has a name, description, participants and image, and the stream URL is `https://www.twitch.tv/stopgameru`
- **THEN** its entry has the name as `SUMMARY`, `URL:https://www.twitch.tv/stopgameru`, an `IMAGE` with the image URL, and a `DESCRIPTION` with the description, the participants and the stream links

### Requirement: Entry description
Each entry's `DESCRIPTION` SHALL be plain text with the event description, the participants and the site's stream links, one `<name>: <url>` line per link, as blocks separated by a blank line. An empty block SHALL be omitted. For stopgame the links SHALL be Twitch (`https://www.twitch.tv/stopgameru`), YouTube (`https://www.youtube.com/@StopgameRuOnline/live`) and StopGame (`https://stopgame.ru/live_schedule`), in this order.

#### Scenario: Event without participants
- **WHEN** an event has a description and no participants
- **THEN** its `DESCRIPTION` contains the description and the stream links

#### Scenario: Event without description and participants
- **WHEN** an event has neither a description nor participants
- **THEN** its `DESCRIPTION` contains only the stream links

#### Scenario: Link names are plain words
- **WHEN** a calendar client turns domain-like text into links, as Apple Calendar does
- **THEN** only the URLs in the stream links are links, not their names (`StopGame`, not `StopGame.ru`)

#### Scenario: Links in calendars that ignore URL
- **WHEN** a calendar client ignores the `URL` property, as Google Calendar does on import
- **THEN** the stream links are still shown in the entry's description

### Requirement: Export failure isolation
A failure to build or write the calendar file SHALL be logged and SHALL NOT prevent saving events or sending Telegram notifications in that run. A later run that saves an event, or finds the file missing, SHALL try to write the file again.

#### Scenario: Write fails
- **WHEN** a run saves a new event and writing the calendar file fails
- **THEN** the event is saved, subscribers receive its notification, and the error is logged

#### Scenario: Retry after failure
- **WHEN** writing the file failed and a later run saves an event
- **THEN** that run writes the file
