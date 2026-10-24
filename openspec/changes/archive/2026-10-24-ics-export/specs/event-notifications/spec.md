# Spec Delta

## MODIFIED Requirements

### Requirement: Recipients
Every notification SHALL be sent to every registered Telegram user, as a photo message with the event image. If no user is registered, processing SHALL still run and save events as usual, and nothing is sent. A user who registers later SHALL NOT be notified about events already stored.

#### Scenario: Several users
- **WHEN** two users are registered and one event produces a notification
- **THEN** each user receives that notification once

#### Scenario: No users
- **WHEN** no user is registered and a new event appears on the site
- **THEN** the run saves the event and sends nothing

#### Scenario: User registers after events were stored
- **WHEN** an event was saved while no user was registered, a user then registers, and the event is unchanged on the next run
- **THEN** the user receives no notification for it

## ADDED Requirements

### Requirement: Telegram is optional
The application SHALL start and process events without Telegram credentials. With no bot token configured, the bot SHALL NOT be registered and no message SHALL be sent, while scraping, saving and the calendar export work as usual. The application SHALL log a warning at startup that Telegram is disabled.

#### Scenario: No bot token
- **WHEN** the application starts without a bot token and a new event appears on the site
- **THEN** the event is saved, the calendar file is updated if export is enabled, and no Telegram message is sent

#### Scenario: Registered users without a bot token
- **WHEN** users are registered but no bot token is configured and an event produces a notification
- **THEN** no Telegram message is sent and no sending error is logged
