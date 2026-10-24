package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.calendar.CalendarEvent;
import com.github.viktor235.schedalertbot.site.stopgame.model.EventStatus;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import com.github.viktor235.schedalertbot.template.FreeMarkerConfig;
import com.github.viktor235.schedalertbot.template.TemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SgCalendarMapperTest {

    private static final String URL = "https://www.twitch.tv/stopgameru";
    private static final Instant DATE = Instant.parse("2025-02-21T20:00:00Z");
    private static final Instant STARTED = Instant.parse("2025-02-21T20:10:00Z");
    private static final Instant ENDED = Instant.parse("2025-02-21T23:05:00Z");
    private static final Instant CREATED = Instant.parse("2025-02-19T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2025-02-20T00:00:00Z");

    private static final String LINKS = """
            Twitch: https://www.twitch.tv/stopgameru
            YouTube: https://www.youtube.com/@StopgameRuOnline/live
            StopGame: https://stopgame.ru/live_schedule""";

    private final SgCalendarMapper mapper = createMapper();

    private static SgCalendarMapper createMapper() {
        SgCalendarMapperImpl mapper = new SgCalendarMapperImpl();
        mapper.templateService = new TemplateService(new FreeMarkerConfig().freemarkerConfig());
        return mapper;
    }

    // Times

    @Test
    void toCalendarEvent_whenScheduled_thenStartsAtDateWithoutEnd() {
        CalendarEvent event = mapper.toCalendarEvent(entry(EventStatus.SCHEDULED, DATE, null, null), URL);

        assertThat(event.start()).isEqualTo(DATE);
        assertThat(event.end()).isNull();
    }

    @Test
    void toCalendarEvent_whenLiveStartedLate_thenStillStartsAtDateWithoutEnd() {
        CalendarEvent event = mapper.toCalendarEvent(entry(EventStatus.LIVE, DATE, STARTED, null), URL);

        assertThat(event.start()).isEqualTo(DATE);
        assertThat(event.end()).isNull();
    }

    @Test
    void toCalendarEvent_whenFinishedWithKnownTiming_thenRealStartAndEnd() {
        CalendarEvent event = mapper.toCalendarEvent(entry(EventStatus.FINISHED, DATE, STARTED, ENDED), URL);

        assertThat(event.start()).isEqualTo(STARTED);
        assertThat(event.end()).isEqualTo(ENDED);
    }

    @Test
    void toCalendarEvent_whenFinishedNeverLive_thenStartsAtDateWithoutEnd() {
        CalendarEvent event = mapper.toCalendarEvent(entry(EventStatus.FINISHED, DATE, null, null), URL);

        assertThat(event.start()).isEqualTo(DATE);
        assertThat(event.end()).isNull();
    }

    @Test
    void toCalendarEvent_whenFinishedWithoutEnd_thenRealStartWithoutEnd() {
        CalendarEvent event = mapper.toCalendarEvent(entry(EventStatus.FINISHED, DATE, STARTED, null), URL);

        assertThat(event.start()).isEqualTo(STARTED);
        assertThat(event.end()).isNull();
    }

    @Test
    void toCalendarEvent_whenFinishedWithEndButUnknownStart_thenStartsAtDateWithoutEnd() {
        CalendarEvent event = mapper.toCalendarEvent(entry(EventStatus.FINISHED, DATE, null, ENDED), URL);

        assertThat(event.start()).isEqualTo(DATE);
        assertThat(event.end()).isNull();
    }

    // Content

    @Test
    void toCalendarEvent_whenFullEntry_thenAllFieldsMapped() {
        SgEventEntry entry = entry(EventStatus.SCHEDULED, DATE, null, null);
        entry.setVersion(3L);

        CalendarEvent event = mapper.toCalendarEvent(entry, URL);

        assertThat(event.sourceId()).isEqualTo("1451");
        assertThat(event.summary()).isEqualTo("Индиана. Sunless Sea");
        assertThat(event.description())
                .isEqualTo("Сходим с ума в подземном море.\n\nАндрей Маковеев, Дмитрий Карнов\n\n" + LINKS);
        assertThat(event.url()).isEqualTo(URL);
        assertThat(event.imageUrl()).isEqualTo("https://images.stopgame.ru/x.jpg");
        assertThat(event.sequence()).isEqualTo(3);
        assertThat(event.lastModified()).isEqualTo(UPDATED);
    }

    @Test
    void toCalendarEvent_whenNoParticipants_thenDescriptionAndLinks() {
        SgEventEntry entry = entry(EventStatus.SCHEDULED, DATE, null, null);
        entry.setParticipants(List.of());

        assertThat(mapper.toCalendarEvent(entry, URL).description()).isEqualTo("Сходим с ума в подземном море.\n\n" + LINKS);
    }

    @Test
    void toCalendarEvent_whenNoDescription_thenParticipantsAndLinks() {
        SgEventEntry entry = entry(EventStatus.SCHEDULED, DATE, null, null);
        entry.setDescription(null);

        assertThat(mapper.toCalendarEvent(entry, URL).description()).isEqualTo("Андрей Маковеев, Дмитрий Карнов\n\n" + LINKS);
    }

    @Test
    void toCalendarEvent_whenNoDescriptionAndParticipants_thenLinksOnly() {
        SgEventEntry entry = entry(EventStatus.SCHEDULED, DATE, null, null);
        entry.setDescription("  ");
        entry.setParticipants(null);

        assertThat(mapper.toCalendarEvent(entry, URL).description()).isEqualTo(LINKS);
    }

    @Test
    void toCalendarEvent_whenNoVersionAndUpdatedAt_thenFallbacks() {
        SgEventEntry entry = entry(EventStatus.SCHEDULED, DATE, null, null);
        entry.setVersion(null);
        entry.setUpdatedAt(null);

        CalendarEvent event = mapper.toCalendarEvent(entry, URL);

        assertThat(event.sequence()).isZero();
        assertThat(event.lastModified()).isEqualTo(CREATED);
    }

    @Test
    void toCalendarEvent_whenNoTimestamps_thenLastModifiedIsStart() {
        SgEventEntry entry = entry(EventStatus.FINISHED, DATE, STARTED, ENDED);
        entry.setUpdatedAt(null);
        entry.setCreatedAt(null);

        assertThat(mapper.toCalendarEvent(entry, URL).lastModified()).isEqualTo(STARTED);
    }

    // Included events

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"SCHEDULED", "LIVE", "FINISHED"})
    void toCalendarEvents_whenIncludedStatus_thenMapped(EventStatus status) {
        assertThat(mapper.toCalendarEvents(List.of(entry(status, DATE, null, null)), URL)).hasSize(1);
    }

    @Test
    void toCalendarEvents_whenCanceled_thenDropped() {
        assertThat(mapper.toCalendarEvents(List.of(entry(EventStatus.CANCELED, DATE, null, null)), URL)).isEmpty();
    }

    @Test
    void toCalendarEvents_whenScheduledWithoutDate_thenDropped() {
        assertThat(mapper.toCalendarEvents(List.of(entry(EventStatus.SCHEDULED, null, null, null)), URL)).isEmpty();
    }

    @Test
    void toCalendarEvents_whenFinishedWithoutDateButStarted_thenKept() {
        List<CalendarEvent> events = mapper.toCalendarEvents(List.of(entry(EventStatus.FINISHED, null, STARTED, ENDED)), URL);

        assertThat(events).extracting(CalendarEvent::start).containsExactly(STARTED);
    }

    @Test
    void toCalendarEvents_whenFinishedWithoutAnyStart_thenDropped() {
        assertThat(mapper.toCalendarEvents(List.of(entry(EventStatus.FINISHED, null, null, null)), URL)).isEmpty();
    }

    private static SgEventEntry entry(EventStatus status, Instant date, Instant startedAt, Instant endedAt) {
        return SgEventEntry.builder()
                .id("1451")
                .status(status)
                .name("Индиана. Sunless Sea")
                .date(date)
                .description("Сходим с ума в подземном море.")
                .participants(List.of("Андрей Маковеев", "Дмитрий Карнов"))
                .imageUrl("https://images.stopgame.ru/x.jpg")
                .startedAt(startedAt)
                .endedAt(endedAt)
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .version(0L)
                .build();
    }
}
