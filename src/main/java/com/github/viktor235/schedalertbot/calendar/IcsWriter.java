package com.github.viktor235.schedalertbot.calendar;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Display;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.List;

/**
 * Renders calendar events as iCalendar (RFC 5545) content.
 * <p>
 * The output depends only on the given events, never on the current time, so equal input gives equal bytes.
 * This is the only class that uses ical4j.
 */
@Component
public class IcsWriter {

    static final String PRODID = "-//viktor-235//sched-alert-bot//EN";
    private static final String UID_DOMAIN = "sched-alert-bot";

    // Definitions come from the ical4j jar; their online refresh is off by default, so the output stays stable
    private final TimeZoneRegistry timeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry();

    /**
     * Writes start and end times in UTC
     *
     * @see #write(String, ZoneId, List)
     */
    public byte[] write(String siteKey, List<CalendarEvent> events) {
        return write(siteKey, null, events);
    }

    /**
     * @param siteKey  site the events come from, part of every UID
     * @param timeZone zone of start and end times, written with TZID and a VTIMEZONE; {@code null} for UTC
     * @param events   events in the order they are written
     */
    public byte[] write(String siteKey, ZoneId timeZone, List<CalendarEvent> events) {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId(PRODID));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        if (timeZone != null) {
            calendar.add(vTimeZone(timeZone));
        }
        events.forEach(event -> calendar.add(toVEvent(siteKey, timeZone, event)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new CalendarOutputter().output(calendar, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static String uid(String siteKey, String sourceId) {
        return sourceId + "@" + siteKey + "." + UID_DOMAIN;
    }

    private VTimeZone vTimeZone(ZoneId timeZone) {
        TimeZone tz = timeZoneRegistry.getTimeZone(timeZone.getId());
        if (tz == null) {
            throw new IllegalArgumentException("No iCalendar time zone definition for " + timeZone.getId());
        }
        return tz.getVTimeZone();
    }

    private VEvent toVEvent(String siteKey, ZoneId timeZone, CalendarEvent event) {
        // No initialisation: the default constructor adds a DTSTAMP of now, which would change the file on every run
        VEvent vEvent = new VEvent(false);
        vEvent.add(new Uid(uid(siteKey, event.sourceId())));
        vEvent.add(new DtStamp(event.lastModified()));
        vEvent.add(new LastModified(event.lastModified()));
        vEvent.add(new Sequence((int) event.sequence()));
        vEvent.add(new DtStart<>(inZone(event.start(), timeZone)));
        if (event.end() != null) {
            vEvent.add(new DtEnd<>(inZone(event.end(), timeZone)));
        }
        if (hasText(event.summary())) {
            vEvent.add(new Summary(event.summary()));
        }
        if (hasText(event.description())) {
            vEvent.add(new Description(event.description()));
        }
        if (hasText(event.url())) {
            vEvent.add(new Url(URI.create(event.url())));
        }
        if (hasText(event.imageUrl())) {
            // RFC 7986; clients without support ignore it
            vEvent.add(new Image(new ParameterList(List.of(Value.URI, new Display("BADGE"))), event.imageUrl()));
        }
        return vEvent;
    }

    // A ZonedDateTime is written as a local time with TZID=<zone id>, an Instant in UTC.
    // ical4j derives the local time from its own definition of the zone, see the patched ZoneRulesBuilder
    private static Temporal inZone(Instant instant, ZoneId timeZone) {
        return timeZone == null ? instant : instant.atZone(timeZone);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
