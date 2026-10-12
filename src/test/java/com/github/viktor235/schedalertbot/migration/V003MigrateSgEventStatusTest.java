package com.github.viktor235.schedalertbot.migration;

import com.github.viktor235.schedalertbot.AbstractIntegrationTest;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class V003MigrateSgEventStatusTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2025-02-20T00:00:00Z");
    private static final Date FUTURE = Date.from(NOW.plus(1, ChronoUnit.DAYS));
    private static final Date PAST = Date.from(NOW.minus(1, ChronoUnit.DAYS));

    @Autowired
    private MongoTemplate mongoTemplate;

    private V003MigrateSgEventStatus migration;

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), SgEventEntry.COLLECTION_NAME);
        migration = new V003MigrateSgEventStatus(mongoTemplate, Clock.fixed(NOW, ZoneOffset.UTC));

        // Documents as stored by v1.1.0: nowLive, no status, no stream timing
        mongoTemplate.insert(List.of(
                legacyEvent("live", true, null),
                legacyEvent("future", false, FUTURE),
                legacyEvent("now", false, Date.from(NOW)),
                legacyEvent("past", false, PAST),
                legacyEvent("no-date", false, null)
        ), SgEventEntry.COLLECTION_NAME);
    }

    @Test
    void changeSet_whenLegacyEvents_thenStatusDerivedAndNowLiveRemoved() {
        migration.changeSet();

        Map<String, Document> events = findAll();
        assertThat(events.get("live").getString("status")).isEqualTo("FINISHED");
        assertThat(events.get("future").getString("status")).isEqualTo("SCHEDULED");
        assertThat(events.get("now").getString("status")).isEqualTo("SCHEDULED");
        assertThat(events.get("past").getString("status")).isEqualTo("FINISHED");
        assertThat(events.get("no-date").getString("status")).isEqualTo("FINISHED");

        assertThat(events.values()).allSatisfy(doc -> {
            assertThat(doc).doesNotContainKeys("nowLive", "startedAt", "endedAt");
        });
        assertThat(events.get("live").get("date")).isNull();
        assertThat(events.get("future").getDate("date")).isEqualTo(FUTURE);
        assertThat(events.get("past").getDate("date")).isEqualTo(PAST);
        assertThat(events.get("no-date").get("date")).isNull();
    }

    @Test
    void changeSet_whenRunAgain_thenNothingChanges() {
        migration.changeSet();
        Map<String, Document> afterFirstRun = findAll();

        migration.changeSet();

        assertThat(findAll()).isEqualTo(afterFirstRun);
    }

    @Test
    void changeSet_whenEventAlreadyHasStatus_thenItIsKept() {
        mongoTemplate.insert(new Document("_id", "current")
                .append("status", "LIVE")
                .append("name", "current"), SgEventEntry.COLLECTION_NAME);

        migration.changeSet();

        assertThat(findAll().get("current").getString("status")).isEqualTo("LIVE");
    }

    @Test
    void rollbackExecution_whenMigrated_thenNowLiveRestoredAndStatusRemoved() {
        mongoTemplate.insert(new Document("_id", "current-live")
                .append("status", "LIVE")
                .append("name", "current-live"), SgEventEntry.COLLECTION_NAME);
        migration.changeSet();

        migration.rollbackExecution();

        Map<String, Document> events = findAll();
        assertThat(events.values()).allSatisfy(doc -> assertThat(doc).doesNotContainKey("status"));
        assertThat(events.get("current-live").getBoolean("nowLive")).isTrue();
        // Lossy by design: legacy live events were migrated to FINISHED
        assertThat(events.get("live").getBoolean("nowLive")).isFalse();
        assertThat(events.get("future").getBoolean("nowLive")).isFalse();
        assertThat(events.get("past").getBoolean("nowLive")).isFalse();
        assertThat(events.get("no-date").getBoolean("nowLive")).isFalse();
    }

    private static Document legacyEvent(String id, boolean nowLive, Date date) {
        Document doc = new Document("_id", id)
                .append("name", id)
                .append("nowLive", nowLive);
        if (date != null) {
            doc.append("date", date);
        }
        return doc;
    }

    private Map<String, Document> findAll() {
        return mongoTemplate.find(new Query(Criteria.where("_id").exists(true)), Document.class, SgEventEntry.COLLECTION_NAME).stream()
                .collect(Collectors.toMap(doc -> doc.getString("_id"), Function.identity()));
    }
}
