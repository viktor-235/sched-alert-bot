package com.github.viktor235.schedalertbot.calendar;

import java.time.Instant;

/**
 * Source-independent calendar entry. Each source maps its stored event to this record.
 *
 * @param sourceId     event id on the source site, unique within the site
 * @param start        resolved start
 * @param end          resolved end; {@code null} means start + default event duration
 * @param summary      event title
 * @param description  event details; may be {@code null}
 * @param url          link to the stream; may be {@code null}
 * @param imageUrl     event poster; may be {@code null}
 * @param sequence     revision number, grows with every change of the event
 * @param lastModified time of the last change of the event
 */
public record CalendarEvent(String sourceId,
                            Instant start,
                            Instant end,
                            String summary,
                            String description,
                            String url,
                            String imageUrl,
                            long sequence,
                            Instant lastModified) {
}
