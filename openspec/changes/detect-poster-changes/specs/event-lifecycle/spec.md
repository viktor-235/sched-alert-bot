# Spec Delta

## MODIFIED Requirements

### Requirement: Save only on real change
An event SHALL be saved when its status changes or at least one of its fields changes, including when the status changes and no field does. A change in the participants list SHALL count as a field change. An image URL that differs from the stored one only in its query string SHALL NOT count as a field change, but the event SHALL still be saved with the new URL. An event whose status and fields are both unchanged SHALL NOT be saved.

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

#### Scenario: Only the image URL query changes
- **WHEN** a SCHEDULED or LIVE event is on the site, keeps its status, and its image URL differs from the stored one only in the query string (`.../poster.jpg?1700000000` → `.../poster.jpg?1700003600`)
- **THEN** the event is saved with the new image URL, and this is not a field change

#### Scenario: Image path changes
- **WHEN** a SCHEDULED event is on the site and its image URL has a different path than the stored one
- **THEN** the event is saved with the new image URL, and this is a field change

#### Scenario: Nothing changes
- **WHEN** a SCHEDULED or LIVE event is on the site with the same status and fields as stored
- **THEN** the event is not saved
