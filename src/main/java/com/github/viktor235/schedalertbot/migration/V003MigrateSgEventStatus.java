package com.github.viktor235.schedalertbot.migration;

import com.github.viktor235.schedalertbot.site.stopgame.model.EventStatus;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;

/**
 * Converts {@code v1.1.0} stopgame events from the {@code nowLive} flag to {@code status}.
 * <p>
 * Live legacy events are treated as finished: their streams ended long ago and their real timing is unknown.
 * Scheduled legacy events are FINISHED when their date has passed or is missing, SCHEDULED otherwise.
 */
@ChangeUnit(id = "migrateSgEventNowLiveToStatus", order = "003", author = "victor")
@RequiredArgsConstructor
public class V003MigrateSgEventStatus {

    public static final String NOW_LIVE_FIELD = "nowLive";
    public static final String STATUS_FIELD = "status";
    public static final String DATE_FIELD = "date";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Execution
    public void changeSet() {
        Instant now = Instant.now(clock);

        setStatus(withoutStatus().and(NOW_LIVE_FIELD).is(true), EventStatus.FINISHED);
        setStatus(withoutStatus().and(NOW_LIVE_FIELD).is(false).and(DATE_FIELD).gte(now), EventStatus.SCHEDULED);
        // Remaining legacy events have a past date or no date
        setStatus(withoutStatus().and(NOW_LIVE_FIELD).exists(true), EventStatus.FINISHED);

        mongoTemplate.updateMulti(
                Query.query(Criteria.where(NOW_LIVE_FIELD).exists(true).and(STATUS_FIELD).exists(true)),
                new Update().unset(NOW_LIVE_FIELD),
                SgEventEntry.COLLECTION_NAME
        );
    }

    /**
     * Lossy: legacy live events were migrated to FINISHED, so they come back with {@code nowLive = false}
     */
    @RollbackExecution
    public void rollbackExecution() {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where(STATUS_FIELD).is(EventStatus.LIVE.name())),
                new Update().set(NOW_LIVE_FIELD, true),
                SgEventEntry.COLLECTION_NAME
        );
        mongoTemplate.updateMulti(
                Query.query(Criteria.where(STATUS_FIELD).exists(true).ne(EventStatus.LIVE.name())),
                new Update().set(NOW_LIVE_FIELD, false),
                SgEventEntry.COLLECTION_NAME
        );
        mongoTemplate.updateMulti(
                Query.query(Criteria.where(STATUS_FIELD).exists(true)),
                new Update().unset(STATUS_FIELD),
                SgEventEntry.COLLECTION_NAME
        );
    }

    private static Criteria withoutStatus() {
        return Criteria.where(STATUS_FIELD).exists(false);
    }

    private void setStatus(Criteria criteria, EventStatus status) {
        mongoTemplate.updateMulti(
                Query.query(criteria),
                new Update().set(STATUS_FIELD, status.name()),
                SgEventEntry.COLLECTION_NAME
        );
    }
}
