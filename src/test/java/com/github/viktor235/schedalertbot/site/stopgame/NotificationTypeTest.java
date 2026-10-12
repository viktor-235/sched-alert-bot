package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.site.stopgame.model.EventStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @ParameterizedTest(name = "{0} -> {1}, field diffs: {2} => {3}")
    @CsvSource(nullValues = "null", value = {
            // New events
            "null,      SCHEDULED, true,  NEW",
            "null,      LIVE,      true,  LIVE",
            // Updates
            "SCHEDULED, SCHEDULED, true,  UPDATE",
            "SCHEDULED, SCHEDULED, false, NONE",
            "LIVE,      SCHEDULED, true,  UPDATE",
            "LIVE,      SCHEDULED, false, NONE",
            // Live
            "SCHEDULED, LIVE,      false, LIVE",
            "SCHEDULED, LIVE,      true,  LIVE",
            "LIVE,      LIVE,      true,  LIVE",
            "LIVE,      LIVE,      false, NONE",
            "CANCELED,  LIVE,      false, LIVE",
            "FINISHED,  LIVE,      false, LIVE",
            // Cancellation
            "SCHEDULED, CANCELED,  true,  CANCELED",
            "null,      CANCELED,  true,  CANCELED",
            "CANCELED,  CANCELED,  false, NONE",
            // Returns
            "CANCELED,  SCHEDULED, false, RETURNED",
            "CANCELED,  SCHEDULED, true,  RETURNED",
            "FINISHED,  SCHEDULED, false, RETURNED",
            "FINISHED,  SCHEDULED, true,  RETURNED",
            // Silent endings
            "LIVE,      FINISHED,  true,  NONE",
            "LIVE,      FINISHED,  false, NONE",
            "SCHEDULED, FINISHED,  true,  NONE",
            "FINISHED,  FINISHED,  false, NONE",
    })
    void of_whenTransition_thenNotificationType(EventStatus prevStatus, EventStatus newStatus,
                                                boolean hasFieldDiffs, NotificationType expected) {
        assertThat(NotificationType.of(prevStatus, newStatus, hasFieldDiffs)).isEqualTo(expected);
    }
}
