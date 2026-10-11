package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.compare.CompareService;
import com.github.viktor235.schedalertbot.site.stopgame.model.*;
import com.github.viktor235.schedalertbot.telegram.TelegramService;
import com.github.viktor235.schedalertbot.template.TemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SgProcessorTest {

    @InjectMocks
    private SgProcessor processor;
    @Mock
    private SgScraper pageParser;
    @Mock
    private SgEventRepository repo;
    @Mock
    private CompareService compareService;
    @Mock
    private TemplateService msgBuildingService;
    @Mock
    private TelegramService tgService;
    @Mock
    private SgMapper mapper;

    @Test
    void updateStatus_whenCanceledEventReturnsToSite_thenStatusIsScheduled() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                createDbEvent(EventStatus.CANCELED),
                createWebEvent(false)
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void updateStatus_whenCanceledEventReturnsToSiteAndIsLive_thenStatusIsLive() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                createDbEvent(EventStatus.CANCELED),
                createWebEvent(true)
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenScheduledEventGoesLive_thenStatusIsLive() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                createDbEvent(EventStatus.SCHEDULED),
                createWebEvent(true)
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenLiveEventDisappears_thenStatusIsFinished() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                createDbEvent(EventStatus.LIVE),
                null
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.FINISHED);
    }

    @Test
    void updateStatus_whenScheduledEventDisappears_thenStatusIsCanceled() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                createDbEvent(EventStatus.SCHEDULED),
                null
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.CANCELED);
    }

    @Test
    void updateStatus_whenNewEventIsLive_thenStatusIsLive() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                null,
                createWebEvent(true)
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.LIVE);
    }

    @Test
    void updateStatus_whenNewEventIsNotLive_thenStatusIsScheduled() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                null,
                createWebEvent(false)
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.SCHEDULED);
    }

    @Test
    void updateStatus_whenEventWithNullStatusDisappears_thenStatusIsCanceled() {
        SgProcessor.EventSnapshot event = SgProcessor.EventSnapshot.init(
                createDbEvent(null),
                null
        );

        SgProcessor.EventSnapshot result = processor.updateStatus(event);

        assertThat(result.newStatus()).isEqualTo(EventStatus.CANCELED);
    }

    private SgEventEntry createDbEvent(EventStatus status) {
        return SgEventEntry.builder()
                .id("123")
                .name("Test Event")
                .status(status)
                .date(Instant.now())
                .description("Test Description")
                .participants(List.of("Participant 1"))
                .imageUrl("https://example.com/image.jpg")
                .build();
    }

    private SgEventWeb createWebEvent(boolean nowLive) {
        return SgEventWeb.builder()
                .id("123")
                .name("Test Event")
                .date(nowLive ? null : Instant.now())
                .description("Test Description")
                .participants(List.of("Participant 1"))
                .nowLive(nowLive)
                .imageUrl("https://example.com/image.jpg")
                .build();
    }
}
