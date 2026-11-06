# Spec Delta

## Purpose

Tells the bot administrators, through Telegram and health metrics, when a scheduled source run fails and when it recovers, without flooding them with a message per failed run.

## ADDED Requirements

### Requirement: Administrators
A registered Telegram user SHALL be an administrator when their stored `admin` flag is true. A user without the flag SHALL NOT be an administrator. The flag is set outside the bot, directly in the database.

#### Scenario: Several administrators
- **WHEN** two users have `admin: true` and an alert is due
- **THEN** each of them receives the alert in their private chat with the bot

#### Scenario: Existing user without the flag
- **WHEN** a user registered before this change has no `admin` field
- **THEN** the user is not an administrator and receives no alerts

#### Scenario: No administrators
- **WHEN** no user has `admin: true` and an alert is due
- **THEN** no Telegram message is sent, the alert is written to the log, and processing continues

### Requirement: Admin flag survives re-registration
Running `/start` again SHALL keep the user's `admin` flag. A new user SHALL be registered without it.

#### Scenario: Admin re-runs /start
- **WHEN** an administrator sends `/start`
- **THEN** their settings are reset as before and they are still an administrator

### Requirement: Alert destination
Alerts SHALL be sent to the administrator's private chat with the bot, never to their target chat.

#### Scenario: Admin with a channel as target chat
- **WHEN** an administrator's target chat is a public channel and an alert is due
- **THEN** the alert goes to their private chat and nothing is posted to the channel

### Requirement: Failure classification
Every failed source run SHALL be classified as exactly one of: BLOCKED (the site answered HTTP 403, 429 or 503), NETWORK (timeout, connection error or any other HTTP error), BROKEN_PAGE (the page loaded but could not be scraped), or INTERNAL (any other error during the run).

#### Scenario: Rate limited
- **WHEN** the site answers HTTP 429
- **THEN** the failure is classified as BLOCKED

#### Scenario: Connection timeout
- **WHEN** fetching the page times out
- **THEN** the failure is classified as NETWORK

#### Scenario: Broken markup
- **WHEN** the page loads but scraping fails
- **THEN** the failure is classified as BROKEN_PAGE

#### Scenario: Database unavailable
- **WHEN** the page is scraped but saving events fails
- **THEN** the failure is classified as INTERNAL

### Requirement: Failure alert
The administrators SHALL be alerted on the first failed run of a source after a successful run or after startup. The alert SHALL name the source, the failure class and the error message, HTML-escaped and shortened to a readable length.

#### Scenario: First failure
- **WHEN** a source run fails after a successful run
- **THEN** one failure alert is sent after that run

#### Scenario: Failure continues
- **WHEN** a failure alert was sent and the source keeps failing with the same class
- **THEN** no further alert is sent until a reminder is due

### Requirement: Failure class change alert
While a source keeps failing, a failed run with a different failure class than the last alert SHALL send a new failure alert immediately.

#### Scenario: Broken page turns into block
- **WHEN** a BROKEN_PAGE alert was sent and the next run fails as BLOCKED
- **THEN** a BLOCKED failure alert is sent after that run

### Requirement: Failure reminder
While a source keeps failing, the administrators SHALL receive a reminder once per configurable interval (default 6 hours) after the last alert, stating since when the source has been failing.

#### Scenario: Long outage
- **WHEN** a source keeps failing for 6 hours after the failure alert
- **THEN** one reminder is sent, and the next one no earlier than 6 hours later

### Requirement: Recovery alert
The first successful run after a failure SHALL send a recovery alert to the administrators, stating the outage duration.

#### Scenario: Recovery
- **WHEN** a source failed and the next run succeeds
- **THEN** one recovery alert is sent, and the next failure sends a new failure alert

#### Scenario: Normal operation
- **WHEN** a source succeeds and the previous run also succeeded
- **THEN** no alert is sent

### Requirement: Failed run side effects
A failed run SHALL NOT send any message to subscribed Telegram users because of the failure. A failure to send an alert SHALL be logged and SHALL NOT stop scheduled processing.

#### Scenario: Subscribers during outage
- **WHEN** a source is BLOCKED and a failure alert is sent
- **THEN** users without the `admin` flag receive no message about it

#### Scenario: Telegram unavailable for alert
- **WHEN** sending an alert fails
- **THEN** the error is logged and the next scheduled run still executes

### Requirement: Health metrics
The Prometheus endpoint SHALL expose a counter of source runs labeled by source and result (`ok` or the failure class in lower case), and a gauge with the time of the last successful run per source, in epoch seconds.

#### Scenario: Successful run
- **WHEN** a stopgame run succeeds
- **THEN** the counter for source `stopgame` and result `ok` increases by one and the last-success gauge is set to the run time

#### Scenario: Blocked run
- **WHEN** a stopgame run fails as BLOCKED
- **THEN** the counter for source `stopgame` and result `blocked` increases by one and the last-success gauge does not change
