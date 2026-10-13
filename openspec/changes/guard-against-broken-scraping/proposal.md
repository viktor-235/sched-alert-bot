# Proposal

## Why

When stopgame.ru changes its markup, the scraper does not fail. It returns wrong data, and the processor treats that data as real. If the event selector stops matching, `parse()` returns an empty list. In one run every SCHEDULED event is then reported to all subscribed Telegram users as "❌ Отмена события", and every LIVE event silently becomes FINISHED. If an inner selector such as `name` breaks, `getString` returns `""`, and every event is announced as "🆙 Обновление события" with an emptied field. An empty list cannot simply be treated as a failure, because the site legitimately shows an empty schedule when no streams are planned.

## What Changes

- The scraper distinguishes an empty schedule from broken markup. A page with zero events is accepted only if the site's explicit empty-schedule placeholder is present. In the current page this is `<div class="empty">Нет запланированных стримов</div>` inside `_streams-list_`, as seen in `test/resources/web/stopgame/events/no-events.html`.
- The placeholder is matched by a new selector in `application.yml` (for example `site.stopgame.scraper.selector.empty`), not in code. Every future source needs the same selector.
- If a page has zero events and no placeholder, the scraper throws `ScraperException`. The processing run is aborted: nothing is saved and no Telegram message is sent.
- Required fields: an event without `id` or `name` fails the whole page with `ScraperException`, instead of producing an entry with empty values. This resolves the existing `//TODO check required fields` in `SgScraper`.
- Affected statuses: SCHEDULED and LIVE events are no longer moved to CANCELED / FINISHED, and no changed fields are reported, because of a scraping failure. All subscribed Telegram users stop receiving these false notifications. Behavior on a correctly parsed page does not change.

## Open Questions

- Which fields are required? The proposal assumes `id` and `name`. `date` already fails on its own (`LocalDateTime.parse` throws), but `participants`, `description` and `imageUrl` can legitimately be empty. Should the participants list also be required?
- Should a partially broken page fail as a whole, or should only the invalid events be skipped? Skipping is dangerous: a skipped SCHEDULED event looks like it disappeared and would be reported as canceled. That is why the proposal fails the whole page.
- Should the scraper also guard against a sharp drop, such as most known events vanishing at once while the page is not empty? This case is not covered. It could be a real schedule change, and any threshold would be a guess.
- How should failures be surfaced? Today an exception only reaches the logs. The README TODO "Send critical exceptions to admins" is the natural follow-up, but it is out of scope unless decided otherwise.
- Should repeated failures be throttled or escalated, for example by logging a single error per outage instead of one every 10 minutes?

## Assumptions

- The placeholder text and markup are stable enough to serve as a structural marker. If the placeholder breaks while the schedule is really empty, the only cost is a skipped run with nothing to process. That failure mode is safe.
- The placeholder is checked only when zero events are found, so a change to its markup has no effect while events are present.
- An aborted run is retried by the next cron tick (`0 1/10 * * * *`); no additional retry logic is needed.
- A fetch failure already throws `ScraperException` (`XpathScraper.parsePage`); this change handles pages that load but parse to wrong data.
- This is not a regression introduced by `fix-event-lifecycle-notifications`, since production behaves the same today. It does not block `v1.2.0`, but the new CANCELED / FINISHED rules make a broken page more visible.

## Capabilities

### New Capabilities
- `schedule-scraping`: how a source's schedule page is turned into events, including how an empty schedule is told apart from broken markup and which fields an event must have for the page to be accepted.

### Modified Capabilities
<!-- none: no specs exist yet -->

## Impact

- Code: `SgScraper` (empty-placeholder check, required fields), possibly `XpathScraper` for a shared helper reusable by other sources.
- Config: new selector in `application.yml` under `site.stopgame.scraper.selector`.
- Tests: `SgScraperTest` with `no-events.html` (accepted) and new fixtures following `test/resources/web/stopgame/events/README.md`, such as a `_broken` page without events or the placeholder, and a page with an empty event title. Plus an integration scenario showing that a broken page saves nothing and sends nothing.
- Database: no schema change.
