# Spec Delta

## ADDED Requirements

### Requirement: Calendar name
Each site's export SHALL have an optional calendar name. When the name is set, the file SHALL contain it as the calendar properties `NAME` (RFC 7986) and `X-WR-CALNAME`. When the name is not set or blank, the file SHALL contain neither property.

#### Scenario: Name set
- **WHEN** the calendar name is `StopGame — Стримы`
- **THEN** the file contains `NAME:StopGame — Стримы` and `X-WR-CALNAME:StopGame — Стримы`

#### Scenario: Name not set
- **WHEN** no calendar name is configured
- **THEN** the file contains no `NAME` and no `X-WR-CALNAME`
