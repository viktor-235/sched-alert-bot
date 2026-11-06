# Spec Delta

## MODIFIED Requirements

### Requirement: Update notification
An event that is SCHEDULED after the run and was SCHEDULED or LIVE before SHALL notify with "🆙 Обновление события" and the card with diff if at least one field changed, including the participants list, and SHALL NOT notify if only the status changed. An image URL that changed only in its query string SHALL NOT count as a changed field.

#### Scenario: Scheduled event changed
- **WHEN** a SCHEDULED event is on the site with a changed date and stays SCHEDULED
- **THEN** subscribers receive "🆙 Обновление события" showing the date as old → new

#### Scenario: Scheduled event participants changed
- **WHEN** a SCHEDULED event is on the site, stays SCHEDULED, and only its participants changed from "Андрей Маковеев, Дмитрий Карнов" to "Андрей Маковеев"
- **THEN** subscribers receive "🆙 Обновление события" with the line `🧑‍🧒‍🧒 Андрей Маковеев, Дмитрий Карнов → Андрей Маковеев`

#### Scenario: Scheduled event poster replaced
- **WHEN** a SCHEDULED event is on the site, stays SCHEDULED, and only its image URL path changed
- **THEN** subscribers receive "🆙 Обновление события" with the line "🖼️ Новый постер"

#### Scenario: Scheduled event poster URL query changed
- **WHEN** a SCHEDULED event is on the site, stays SCHEDULED, and only the query string of its image URL changed
- **THEN** no notification is sent

#### Scenario: Live event shown as not live with changes
- **WHEN** a LIVE event is on the site, no longer live, and its name changed
- **THEN** subscribers receive "🆙 Обновление события" showing the name change

#### Scenario: Live event shown as not live without changes
- **WHEN** a LIVE event is on the site, no longer live, and none of its fields changed
- **THEN** the event is saved as SCHEDULED and no notification is sent

### Requirement: Live notification
An existing event SHALL notify with "🔴 В эфире" and the card with diff when it enters LIVE from any other status, even if no field changed, and when it stays LIVE and at least one field changed. An image URL that changed only in its query string SHALL NOT count as a changed field.

#### Scenario: Scheduled event goes live without other changes
- **WHEN** a SCHEDULED event goes live and none of its fields changed
- **THEN** subscribers receive "🔴 В эфире" with the card

#### Scenario: Live event changed
- **WHEN** a LIVE event stays live and its description changes
- **THEN** subscribers receive "🔴 В эфире" showing the description change

#### Scenario: Live event poster URL query changed
- **WHEN** a LIVE event stays live and only the query string of its image URL changed
- **THEN** no notification is sent

#### Scenario: Canceled event returns live
- **WHEN** a CANCELED event is on the site again and is live
- **THEN** subscribers receive "🔴 В эфире" with the card

#### Scenario: Finished event returns live
- **WHEN** a FINISHED event is on the site again and is live
- **THEN** subscribers receive "🔴 В эфире" with the card

### Requirement: Card content
A card SHALL list the event name, date, participants and description, omitting any that are empty on both sides. The date SHALL be omitted under the "🔴 В эфире" header. A changed image SHALL be shown as "Новый постер". An image URL that changed only in its query string SHALL NOT be shown as "Новый постер".

#### Scenario: Live card has no date
- **WHEN** a "🔴 В эфире" notification is sent for an event with a stored `date`
- **THEN** the message contains no date line

#### Scenario: Empty field is omitted
- **WHEN** a new event has no participants
- **THEN** the card has no participants line

#### Scenario: Poster URL query changed together with another field
- **WHEN** a SCHEDULED event stays SCHEDULED, its date changed, and only the query string of its image URL changed
- **THEN** subscribers receive "🆙 Обновление события" with the date change and without "🖼️ Новый постер"
