package com.github.viktor235.schedalertbot.calendar;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-site iCalendar export settings, e.g. {@code site.stopgame.ics.*}
 *
 * @param enabled              whether the site's calendar file is written
 * @param path                 calendar file location
 * @param defaultEventDuration event duration when the real one is unknown
 * @param pastPeriod           how far back events are kept in the file
 * @param url                  stream link put into every event
 * @param timeZone             zone of event start and end times; {@code null} writes them in UTC
 * @param name                 calendar name shown by calendar apps; {@code null} writes none
 */
public record IcsExportProperties(@DefaultValue("false") boolean enabled,
                                  Path path,
                                  Duration defaultEventDuration,
                                  @DefaultValue("3y") Period pastPeriod,
                                  String url,
                                  ZoneId timeZone,
                                  String name) {

    public IcsExportProperties {
        if (enabled) {
            List<String> missing = new ArrayList<>();
            if (path == null) missing.add("path");
            if (url == null || url.isBlank()) missing.add("url");
            if (defaultEventDuration == null) missing.add("default-event-duration");
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Calendar export is enabled, but required settings are missing: " + missing);
            }
        }
    }

    /**
     * Binds the settings under {@code prefix}; a missing section gives the defaults (export disabled)
     */
    public static IcsExportProperties bind(Binder binder, String prefix) {
        return binder.bindOrCreate(prefix, IcsExportProperties.class);
    }
}
