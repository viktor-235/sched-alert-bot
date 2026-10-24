# calendar-publishing Specification

## Purpose

Publishes a site's exported `.ics` file to a GitHub repository served by GitHub Pages, so that calendar apps can subscribe to the schedule by a stable public URL.

## Requirements

### Requirement: Publishing is optional and per site
Each site SHALL have its own publishing settings: enabled flag, repository (`owner/name`), branch, file path in the repository, minimum interval between commits, access token, API URL and commit message. Publishing SHALL be disabled by default. The branch SHALL default to `main`, the minimum interval to 10 minutes and the API URL to `https://api.github.com`. When publishing is disabled, the site's processing SHALL NOT call GitHub.

#### Scenario: Disabled by default
- **WHEN** a site has no publishing settings and a run changes the calendar file
- **THEN** no request is made to GitHub

### Requirement: Publishing settings are checked at startup
When publishing is enabled, the repository, path and token SHALL be required and the site's calendar export SHALL be enabled; otherwise the application SHALL fail to start with a message naming the problem. The token SHALL be read from the environment and SHALL NOT appear in logs.

#### Scenario: Missing required setting
- **WHEN** publishing is enabled without a token
- **THEN** the application fails to start, naming the missing setting

#### Scenario: Export disabled
- **WHEN** publishing is enabled and the site's calendar export is disabled
- **THEN** the application fails to start

### Requirement: What is published
The published file SHALL be the site's local calendar file byte for byte, written to the configured path and branch of the configured repository. Publishing SHALL change only that file in the repository. Event statuses in the published file SHALL be those of the local export: SCHEDULED, LIVE and FINISHED events, no CANCELED ones.

#### Scenario: First publish
- **WHEN** publishing is enabled, the local file exists, and the repository has no file at the path
- **THEN** the file is created at the path with the local content in one commit

#### Scenario: Canceled event
- **WHEN** an event is canceled and the local file is rewritten without it
- **THEN** the next published file has no entry for that event

### Requirement: Commit only changed content
After each run of the site's processing, whether or not it saved anything, the local file SHALL be published if its content differs from the published file, subject to the minimum interval. A file equal to the published one SHALL NOT be committed, including after a restart of the application. A missing local file SHALL NOT be published and SHALL NOT be deleted from the repository.

#### Scenario: Unchanged content
- **WHEN** the local file equals the published file
- **THEN** no commit is made

#### Scenario: Restart with unchanged content
- **WHEN** the application starts and the local file equals the published file
- **THEN** no commit is made

#### Scenario: Changed content
- **WHEN** the local file differs from the published file and no publish was attempted within the minimum interval
- **THEN** one commit replaces the published file with the local content

#### Scenario: Local file missing
- **WHEN** publishing is enabled and the local file does not exist
- **THEN** no request is made and the published file stays as it is

### Requirement: Minimum interval between commits
A publish SHALL be attempted only when at least the minimum interval has passed since the last publish attempt. Changes made within the interval SHALL be published together by one later commit carrying the latest content.

#### Scenario: Change within the interval
- **WHEN** a commit was made and the local file changes again before the minimum interval has passed
- **THEN** no commit is made until the interval has passed, and then one commit publishes the latest content

#### Scenario: Run without saves catches up
- **WHEN** a change was not published earlier and a later run saves nothing
- **THEN** that run publishes the change once the interval has passed

### Requirement: Outdated published version
When the published file has changed since the bot last read it, a commit SHALL be based on the current published version: the bot SHALL re-read it and retry the commit once. If the re-read file already equals the local file, no commit SHALL be made.

#### Scenario: File changed in the repository
- **WHEN** the published file was changed by someone else and the bot commits a new local file
- **THEN** the bot re-reads the published file and commits the local content over it

#### Scenario: Same content already published
- **WHEN** the commit is rejected as outdated and the re-read published file equals the local file
- **THEN** no further commit is made

### Requirement: Failure isolation
A publishing failure (network error, rejected or expired token, GitHub error) SHALL be logged and SHALL NOT affect scraping, saving events, the local export or Telegram notifications of the run. A failed publish SHALL be retried by a later run after the minimum interval.

#### Scenario: GitHub unavailable
- **WHEN** a run saves events and the publish request fails
- **THEN** the events are saved, the local file is written, notifications are sent, and the error is logged

#### Scenario: Retry after failure
- **WHEN** a publish failed and the minimum interval has passed
- **THEN** the next run publishes the file

#### Scenario: No retry within the interval
- **WHEN** a publish failed and the next run is within the minimum interval
- **THEN** that run makes no request to GitHub
