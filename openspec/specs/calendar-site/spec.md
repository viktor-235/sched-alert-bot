# calendar-site Specification

## Purpose

The static site of the calendars repository, served by GitHub Pages next to the published `.ics` files. GitHub Pages lets browsers reuse a file for up to 10 minutes without asking for it again, so a reloaded page can get new markup together with old styles, scripts and texts.

## Requirements

### Requirement: Versioned site assets
Site pages SHALL load every local stylesheet, script and texts file with a `?v=<N>` query, using the same `N` on all pages. Every published change to the site's files SHALL increase `N` by one. The published `.ics` files SHALL NOT be versioned: the bot rewrites them, and the page loads them with revalidation.

#### Scenario: Site change is published
- **WHEN** a commit changes any site file, such as a page, a stylesheet, a script, a texts file or a vendored library
- **THEN** the same commit increases `N` by one in all references on all pages

#### Scenario: Browser holds old assets
- **WHEN** a browser has cached the previous version's assets and reloads a page of the new version
- **THEN** the page loads the new version's assets, not the cached ones

#### Scenario: Texts follow the page version
- **WHEN** a page loads its texts file
- **THEN** the request carries the same `?v=<N>` as the page's scripts

#### Scenario: Calendar file changes
- **WHEN** the bot publishes a new `.ics` file and no site file changes
- **THEN** `N` stays the same
