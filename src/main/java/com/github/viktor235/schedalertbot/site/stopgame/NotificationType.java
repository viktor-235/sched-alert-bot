package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.site.stopgame.model.EventStatus;

/**
 * Kind of notification produced by an event lifecycle transition
 */
public enum NotificationType {

    NEW,
    UPDATE,
    LIVE,
    CANCELED,
    RETURNED,
    NONE;

    /**
     * Maps a lifecycle transition to the notification subscribers receive.
     *
     * @param prevStatus    stored status, {@code null} for a new event
     * @param newStatus     status after this run
     * @param hasFieldDiffs whether at least one event field changed
     */
    public static NotificationType of(EventStatus prevStatus, EventStatus newStatus, boolean hasFieldDiffs) {
        if (prevStatus == null && (newStatus == EventStatus.LIVE || newStatus == EventStatus.SCHEDULED)) {
            return newStatus == EventStatus.LIVE ? LIVE : NEW;
        }
        return switch (newStatus) {
            case LIVE -> prevStatus != EventStatus.LIVE || hasFieldDiffs ? LIVE : NONE;
            case SCHEDULED -> switch (prevStatus) {
                case SCHEDULED, LIVE -> hasFieldDiffs ? UPDATE : NONE;
                case CANCELED, FINISHED -> RETURNED;
            };
            case CANCELED -> prevStatus != EventStatus.CANCELED ? CANCELED : NONE;
            case FINISHED -> NONE;
        };
    }
}
