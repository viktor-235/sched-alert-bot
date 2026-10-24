package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.calendar.CalendarExporter;
import com.github.viktor235.schedalertbot.calendar.IcsExportProperties;
import com.github.viktor235.schedalertbot.calendar.IcsWriter;
import com.github.viktor235.schedalertbot.compare.CompareService;
import com.github.viktor235.schedalertbot.site.stopgame.model.*;
import com.github.viktor235.schedalertbot.telegram.TelegramService;
import com.github.viktor235.schedalertbot.telegram.TelegramUser;
import com.github.viktor235.schedalertbot.template.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SgProcessorTest {

    private static final Instant NOW = Instant.parse("2025-02-20T00:00:00Z");
    private static final Instant SCHEDULED_DATE = NOW.plus(1, ChronoUnit.DAYS);
    private static final Instant EARLIER = NOW.minus(1, ChronoUnit.HOURS);

    private SgProcessor processor;
    @Mock
    private SgScraper pageParser;
    @Mock
    private SgEventRepository repo;
    @Mock
    private TemplateService msgBuildingService;
    @Mock
    private TelegramService tgService;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        processor = createProcessor(new IcsExportProperties(false, tempDir.resolve("stopgame.ics"), Duration.ofMinutes(120), Period.ofYears(3), "https://example.com", null));
        when(tgService.getUsers()).thenReturn(List.of(TelegramUser.builder().id("1").targetChatId("1").build()));
        when(msgBuildingService.buildMsg(any(), any())).thenReturn("msg");
        when(repo.findAllByIdIn(any())).thenReturn(List.of());
        when(repo.findAllByStatusIn(any())).thenReturn(List.of());
    }

    // Status resolution

    @Test
    void updateStatus_whenCanceledEventReturnsToSite_thenStatusIsScheduled() {
        assertThat(resolveStatus(createDbEvent(EventStatus.CANCELED), createWebEvent(false)))
                .isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void updateStatus_whenCanceledEventReturnsToSiteAndIsLive_thenStatusIsLive() {
        assertThat(resolveStatus(createDbEvent(EventStatus.CANCELED), createWebEvent(true)))
                .isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenFinishedEventReturnsToSite_thenStatusIsScheduled() {
        assertThat(resolveStatus(createDbEvent(EventStatus.FINISHED), createWebEvent(false)))
                .isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void updateStatus_whenFinishedEventReturnsToSiteAndIsLive_thenStatusIsLive() {
        assertThat(resolveStatus(createDbEvent(EventStatus.FINISHED), createWebEvent(true)))
                .isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenLiveEventIsShownAsNotLive_thenStatusIsScheduled() {
        assertThat(resolveStatus(createDbEvent(EventStatus.LIVE), createWebEvent(false)))
                .isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void updateStatus_whenScheduledEventGoesLive_thenStatusIsLive() {
        assertThat(resolveStatus(createDbEvent(EventStatus.SCHEDULED), createWebEvent(true)))
                .isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenLiveEventDisappears_thenStatusIsFinished() {
        assertThat(resolveStatus(createDbEvent(EventStatus.LIVE), null))
                .isEqualTo(EventStatus.FINISHED);
    }

    @Test
    void updateStatus_whenScheduledEventDisappearsBeforeItsDate_thenStatusIsCanceled() {
        assertThat(resolveStatus(createDbEvent(EventStatus.SCHEDULED, NOW.plusSeconds(1)), null))
                .isEqualTo(EventStatus.CANCELED);
    }

    @Test
    void updateStatus_whenScheduledEventDisappearsExactlyAtItsDate_thenStatusIsCanceled() {
        assertThat(resolveStatus(createDbEvent(EventStatus.SCHEDULED, NOW), null))
                .isEqualTo(EventStatus.CANCELED);
    }

    @Test
    void updateStatus_whenScheduledEventDisappearsAfterItsDate_thenStatusIsFinished() {
        assertThat(resolveStatus(createDbEvent(EventStatus.SCHEDULED, NOW.minusSeconds(1)), null))
                .isEqualTo(EventStatus.FINISHED);
    }

    @Test
    void updateStatus_whenScheduledEventWithoutDateDisappears_thenStatusIsFinished() {
        assertThat(resolveStatus(createDbEvent(EventStatus.SCHEDULED, null), null))
                .isEqualTo(EventStatus.FINISHED);
    }

    @Test
    void updateStatus_whenNewEventIsLive_thenStatusIsLive() {
        assertThat(resolveStatus(null, createWebEvent(true)))
                .isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenNewEventIsNotLive_thenStatusIsScheduled() {
        assertThat(resolveStatus(null, createWebEvent(false)))
                .isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void updateStatus_whenEventWithNullStatusDisappears_thenStatusIsCanceled() {
        assertThat(resolveStatus(createDbEvent(null), null))
                .isEqualTo(EventStatus.CANCELED);
    }

    // Stable comparison

    @Test
    void compare_whenLiveEventWithStoredDate_thenNoDateDiffAndDateIsKept() {
        SgEventEntry db = createDbEvent(EventStatus.LIVE);

        SgProcessor.EventSnapshot snapshot = processor.compare(processor.normalize(
                SgProcessor.EventSnapshot.init(db, createWebEvent(true))));

        assertThat(snapshot.fieldDiffs()).isEmpty();
        assertThat(snapshot.web().getDate()).isEqualTo(SCHEDULED_DATE);
    }

    @Test
    void process_whenScheduledEventGoesLive_thenSavedEntryKeepsDate() {
        givenDb(createDbEvent(EventStatus.SCHEDULED));
        givenSite(createWebEvent(true));

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(saved.getDate()).isEqualTo(SCHEDULED_DATE);
        verify(tgService).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenNewEventIsLive_thenDateIsEmpty() {
        givenSite(createWebEvent(true));

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(saved.getDate()).isNull();
    }

    // Calendar export

    @Test
    void process_whenExportDisabledAndEventSaved_thenNoCalendarFile() {
        givenSite(createWebEvent(false));

        processor.process();

        capturedSave();
        assertThat(tempDir.resolve("stopgame.ics")).doesNotExist();
    }

    @Test
    void process_whenExportDisabledAndFileExists_thenFileUnchanged() throws IOException {
        Path file = tempDir.resolve("stopgame.ics");
        Files.writeString(file, "old content");
        givenSite(createWebEvent(false));

        processor.process();

        capturedSave();
        assertThat(file).hasContent("old content");
    }

    // Save / notify decisions

    @Test
    void process_whenLiveEventDisappears_thenSavedWithoutNotification() {
        givenDb(createDbEvent(EventStatus.LIVE));
        givenSite();

        processor.process();

        assertThat(capturedSave().getStatus()).isEqualTo(EventStatus.FINISHED);
        verify(tgService, never()).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenLiveEventUnchanged_thenNotSavedAndNotNotified() {
        givenDb(createDbEvent(EventStatus.LIVE));
        givenSite(createWebEvent(true));

        processor.process();

        verify(repo, never()).save(any());
        verify(tgService, never()).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenFinishedEventReturnsWithoutChanges_thenSavedAndNotified() {
        SgEventEntry db = createDbEvent(EventStatus.FINISHED);
        when(repo.findAllByIdIn(any())).thenReturn(List.of(db));
        givenSite(createWebEvent(false));

        processor.process();

        assertThat(capturedSave().getStatus()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(capturedNotification()).isEqualTo(NotificationType.RETURNED.name());
        verify(tgService).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenScheduledEventParticipantsChanged_thenSavedAndNotifiedWithUpdate() {
        givenDb(createDbEvent(EventStatus.SCHEDULED));
        SgEventWeb web = createWebEvent(false);
        web.setParticipants(List.of("Participant 1", "Participant 2"));
        givenSite(web);

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(saved.getParticipants()).containsExactly("Participant 1", "Participant 2");
        assertThat(capturedNotification()).isEqualTo(NotificationType.UPDATE.name());
        verify(tgService).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenLiveEventParticipantsChanged_thenSavedAndNotifiedWithLive() {
        givenDb(createDbEvent(EventStatus.LIVE));
        SgEventWeb web = createWebEvent(true);
        web.setParticipants(List.of("Participant 2"));
        givenSite(web);

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(saved.getParticipants()).containsExactly("Participant 2");
        assertThat(capturedNotification()).isEqualTo(NotificationType.LIVE.name());
        verify(tgService).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenStoredParticipantsMissingAndSiteShowsNone_thenNotSavedAndNotNotified() {
        SgEventEntry db = createDbEvent(EventStatus.SCHEDULED);
        db.setParticipants(null);
        givenDb(db);
        SgEventWeb web = createWebEvent(false);
        web.setParticipants(List.of());
        givenSite(web);

        processor.process();

        verify(repo, never()).save(any());
        verify(tgService, never()).sendPhotoMessage(any(), any(), any());
    }

    @Test
    void process_whenSaveFails_thenNothingIsSent() {
        givenDb(createDbEvent(EventStatus.SCHEDULED));
        givenSite(createWebEvent(true));
        when(repo.save(any())).thenThrow(new IllegalStateException("db down"));

        try {
            processor.process();
        } catch (IllegalStateException ignored) {
            // expected
        }

        verify(tgService, never()).sendPhotoMessage(any(), any(), any());
    }

    // Stream timing

    @Test
    void process_whenScheduledEventGoesLive_thenStartedAtIsNowAndEndedAtIsEmpty() {
        givenDb(createDbEvent(EventStatus.SCHEDULED));
        givenSite(createWebEvent(true));

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStartedAt()).isEqualTo(NOW);
        assertThat(saved.getEndedAt()).isNull();
    }

    @Test
    void process_whenNewEventIsLive_thenStartedAtIsNowAndEndedAtIsEmpty() {
        givenSite(createWebEvent(true));

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStartedAt()).isEqualTo(NOW);
        assertThat(saved.getEndedAt()).isNull();
    }

    @Test
    void process_whenFinishedEventReturnsLive_thenStartedAtIsNowAndEndedAtIsCleared() {
        SgEventEntry db = createDbEvent(EventStatus.FINISHED);
        db.setStartedAt(EARLIER.minus(2, ChronoUnit.HOURS));
        db.setEndedAt(EARLIER);
        when(repo.findAllByIdIn(any())).thenReturn(List.of(db));
        givenSite(createWebEvent(true));

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.LIVE);
        assertThat(saved.getStartedAt()).isEqualTo(NOW);
        assertThat(saved.getEndedAt()).isNull();
    }

    @Test
    void process_whenLiveEventFinishes_thenEndedAtIsNowAndStartedAtIsKept() {
        SgEventEntry db = createDbEvent(EventStatus.LIVE);
        db.setStartedAt(EARLIER);
        givenDb(db);
        givenSite();

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStartedAt()).isEqualTo(EARLIER);
        assertThat(saved.getEndedAt()).isEqualTo(NOW);
    }

    @Test
    void process_whenScheduledEventIsCanceled_thenTimingIsUnchanged() {
        givenDb(createDbEvent(EventStatus.SCHEDULED));
        givenSite();

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.CANCELED);
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getEndedAt()).isNull();
    }

    @Test
    void process_whenScheduledEventFinishesUnseen_thenTimingStaysEmpty() {
        givenDb(createDbEvent(EventStatus.SCHEDULED, EARLIER));
        givenSite();

        processor.process();

        SgEventEntry saved = capturedSave();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.FINISHED);
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getEndedAt()).isNull();
        verify(tgService, never()).sendPhotoMessage(any(), any(), any());
    }

    // Duplicate ids

    @Test
    void process_whenDuplicateIdsOnPage_thenFirstOccurrenceIsProcessed() {
        SgEventWeb first = createWebEvent(false);
        SgEventWeb duplicate = createWebEvent(false);
        duplicate.setName("Duplicate");
        SgEventWeb other = createWebEvent(false);
        other.setId("456");
        givenSite(first, duplicate, other);

        processor.process();

        ArgumentCaptor<SgEventEntry> captor = ArgumentCaptor.forClass(SgEventEntry.class);
        verify(repo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SgEventEntry::getId, SgEventEntry::getName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("123", "Test Event"),
                        org.assertj.core.groups.Tuple.tuple("456", "Test Event"));
    }

    private EventStatus resolveStatus(SgEventEntry db, SgEventWeb web) {
        return processor.updateStatus(SgProcessor.EventSnapshot.init(db, web)).newStatus();
    }

    private void givenSite(SgEventWeb... events) {
        when(pageParser.parse()).thenReturn(List.of(events));
    }

    private void givenDb(SgEventEntry... events) {
        when(repo.findAllByStatusIn(any())).thenReturn(List.of(events));
    }

    private SgEventEntry capturedSave() {
        ArgumentCaptor<SgEventEntry> captor = ArgumentCaptor.forClass(SgEventEntry.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Object capturedNotification() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(msgBuildingService).buildMsg(eq(SgProcessor.TEMPLATE_NAME), captor.capture());
        return captor.getValue().get("notification");
    }

    private SgProcessor createProcessor(IcsExportProperties icsProps) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new SgProcessor(pageParser, repo, new CompareService(), msgBuildingService, tgService,
                new SgMapperImpl(), clock, new CalendarExporter("stopgame", icsProps, new IcsWriter(), clock),
                new SgCalendarMapperImpl(), icsProps);
    }

    private SgEventEntry createDbEvent(EventStatus status) {
        return createDbEvent(status, SCHEDULED_DATE);
    }

    private SgEventEntry createDbEvent(EventStatus status, Instant date) {
        return SgEventEntry.builder()
                .id("123")
                .name("Test Event")
                .status(status)
                .date(date)
                .description("Test Description")
                .participants(new ArrayList<>(List.of("Participant 1"))) // MapStruct updates the list in place
                .imageUrl("https://example.com/image.jpg")
                .build();
    }

    private SgEventWeb createWebEvent(boolean nowLive) {
        return SgEventWeb.builder()
                .id("123")
                .name("Test Event")
                .date(nowLive ? null : SCHEDULED_DATE)
                .description("Test Description")
                .participants(List.of("Participant 1"))
                .nowLive(nowLive)
                .imageUrl("https://example.com/image.jpg")
                .build();
    }
}
