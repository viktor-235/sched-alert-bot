package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.calendar.CalendarEvent;
import com.github.viktor235.schedalertbot.site.stopgame.model.EventStatus;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import com.github.viktor235.schedalertbot.template.TemplateService;
import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps stored Stopgame events to calendar events.
 * <p>
 * Times depend on the status, so that the entry does not change while a stream is running:
 * SCHEDULED and LIVE use the scheduled date; FINISHED uses the real stream start and end.
 * The description is rendered from {@value #DESCRIPTION_TEMPLATE}.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class SgCalendarMapper {

    public static final String DESCRIPTION_TEMPLATE = "stopgame-calendar.txt.ftl";

    // Field injection: the generated subclass is the Spring bean
    @Autowired
    protected TemplateService templateService;

    /**
     * Maps the events that belong in the calendar: CANCELED events and events without a start are dropped
     */
    public List<CalendarEvent> toCalendarEvents(List<SgEventEntry> entries, String url) {
        return entries.stream()
                .filter(e -> e.getStatus() != EventStatus.CANCELED && calendarStart(e) != null)
                .map(e -> toCalendarEvent(e, url))
                .toList();
    }

    /**
     * Derived fields use expressions, not {@code source = "entry"}: mapping the whole source parameter
     * makes MapStruct skip the unmapped source check for all of its properties.
     * This way a new {@link SgEventEntry} field fails the build until it is mapped or listed as ignored.
     */
    @Mapping(target = "sourceId", source = "entry.id")
    @Mapping(target = "summary", source = "entry.name")
    @Mapping(target = "imageUrl", source = "entry.imageUrl")
    @Mapping(target = "url", source = "url")
    @Mapping(target = "sequence", source = "entry.version", defaultValue = "0L")
    @Mapping(target = "start", expression = "java(calendarStart(entry))")
    @Mapping(target = "end", expression = "java(calendarEnd(entry))")
    @Mapping(target = "description", expression = "java(calendarDescription(entry))")
    @Mapping(target = "lastModified", expression = "java(calendarLastModified(entry))")
    // Used by the expressions above
    @BeanMapping(ignoreUnmappedSourceProperties = {"status", "date", "description", "participants", "startedAt", "endedAt", "createdAt", "updatedAt"})
    public abstract CalendarEvent toCalendarEvent(SgEventEntry entry, String url);

    protected Instant calendarStart(SgEventEntry entry) {
        if (entry.getStatus() == EventStatus.FINISHED && entry.getStartedAt() != null) {
            return entry.getStartedAt(); // The stream really started
        }
        return entry.getDate(); // A live stream keeps its scheduled time, so the file does not change while it runs
    }

    /**
     * Real end of a finished stream; {@code null} means the default duration
     */
    protected Instant calendarEnd(SgEventEntry entry) {
        if (entry.getStatus() == EventStatus.FINISHED && entry.getStartedAt() != null) {
            return entry.getEndedAt();
        }
        return null;
    }

    protected String calendarDescription(SgEventEntry entry) {
        // HashMap: the template checks for missing values, which Map.of does not allow
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("description", entry.getDescription());
        ctx.put("participants", entry.getParticipants());
        return templateService.buildMsg(DESCRIPTION_TEMPLATE, ctx).strip();
    }

    protected Instant calendarLastModified(SgEventEntry entry) {
        return ObjectUtils.firstNonNull(entry.getUpdatedAt(), entry.getCreatedAt(), calendarStart(entry));
    }
}
