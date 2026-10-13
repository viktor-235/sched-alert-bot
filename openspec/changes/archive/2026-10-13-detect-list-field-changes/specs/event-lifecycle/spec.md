# Spec Delta

## MODIFIED Requirements

### Requirement: Save only on real change
An event SHALL be saved when its status changes or at least one of its fields changes, including when the status changes and no field does. A change in the participants list SHALL count as a field change. An event whose status and fields are both unchanged SHALL NOT be saved.

#### Scenario: Status changes, fields do not
- **WHEN** a SCHEDULED event goes live and none of its fields changed
- **THEN** the event is saved with status LIVE

#### Scenario: Fields change, status does not
- **WHEN** a SCHEDULED event is on the site with a changed description
- **THEN** the event is saved with the new description and status SCHEDULED

#### Scenario: Only participants change
- **WHEN** a SCHEDULED event is on the site and only its participants list changed
- **THEN** the event is saved with the new participants and status SCHEDULED

#### Scenario: Participants change while live
- **WHEN** a LIVE event stays live and only its participants list changed
- **THEN** the event is saved with the new participants and status LIVE

#### Scenario: Missing and empty participants are equal
- **WHEN** a stored event has no participants list and the site shows the event with no participants
- **THEN** this is not a field change

#### Scenario: Nothing changes
- **WHEN** a SCHEDULED or LIVE event is on the site with the same status and fields as stored
- **THEN** the event is not saved
