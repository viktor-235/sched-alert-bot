# Spec Delta

## MODIFIED Requirements

### Requirement: Update notification
An event that is SCHEDULED after the run and was SCHEDULED or LIVE before SHALL notify with "🆙 Обновление события" and the card with diff if at least one field changed, including the participants list, and SHALL NOT notify if only the status changed.

#### Scenario: Scheduled event changed
- **WHEN** a SCHEDULED event is on the site with a changed date and stays SCHEDULED
- **THEN** subscribers receive "🆙 Обновление события" showing the date as old → new

#### Scenario: Scheduled event participants changed
- **WHEN** a SCHEDULED event is on the site, stays SCHEDULED, and only its participants changed from "Андрей Маковеев, Дмитрий Карнов" to "Андрей Маковеев"
- **THEN** subscribers receive "🆙 Обновление события" with the line `🧑‍🧒‍🧒 Андрей Маковеев, Дмитрий Карнов → Андрей Маковеев`

#### Scenario: Live event shown as not live with changes
- **WHEN** a LIVE event is on the site, no longer live, and its name changed
- **THEN** subscribers receive "🆙 Обновление события" showing the name change

#### Scenario: Live event shown as not live without changes
- **WHEN** a LIVE event is on the site, no longer live, and none of its fields changed
- **THEN** the event is saved as SCHEDULED and no notification is sent

### Requirement: Cancellation notification
A SCHEDULED event that becomes CANCELED SHALL notify with "❌ Отмена события" and the card built from the stored values.

#### Scenario: Scheduled event canceled
- **WHEN** a SCHEDULED event becomes CANCELED
- **THEN** subscribers receive "❌ Отмена события" with the stored name, date, participants and description

#### Scenario: Canceled event without participants
- **WHEN** a SCHEDULED event with no stored participants becomes CANCELED
- **THEN** the "❌ Отмена события" card has no participants line

### Requirement: Diff rendering
In a card with diff, each changed field SHALL be shown as old value → new value, except that an old non-empty description is shortened to "..."; unchanged fields are shown with their current value. An empty side of a change SHALL be shown as italic "пусто" using valid Telegram HTML (`<i>пусто</i>`). The message SHALL NOT contain the literal text `<пусто>`.

#### Scenario: Description added
- **WHEN** an event had no description and now has "Описание стрима"
- **THEN** the message contains `ℹ️ <i>пусто</i> → Описание стрима`

#### Scenario: Description removed
- **WHEN** an event had description "Описание стрима" and now has none
- **THEN** the message contains `ℹ️ ... → <i>пусто</i>`

#### Scenario: Participants removed
- **WHEN** an event's participants list changes from two names to empty
- **THEN** the message shows the old names followed by ` → <i>пусто</i>`

#### Scenario: Participants added
- **WHEN** an event's participants list changes from empty to "Андрей Маковеев"
- **THEN** the message contains `🧑‍🧒‍🧒 <i>пусто</i> → Андрей Маковеев`
