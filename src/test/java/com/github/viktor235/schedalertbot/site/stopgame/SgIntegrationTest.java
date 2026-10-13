package com.github.viktor235.schedalertbot.site.stopgame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.viktor235.schedalertbot.AbstractIntegrationTest;
import com.github.viktor235.schedalertbot.site.stopgame.model.EventStatus;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventRepository;
import com.github.viktor235.schedalertbot.telegram.TelegramService;
import com.github.viktor235.schedalertbot.telegram.TelegramUser;
import com.github.viktor235.schedalertbot.telegram.TelegramUserRepository;
import com.github.viktor235.schedalertbot.utils.JsonAssertions;
import com.github.viktor235.schedalertbot.utils.MutableClock;
import com.github.viktor235.schedalertbot.utils.TestUtils;
import com.github.viktor235.schedalertbot.web.XpathScraper;
import lombok.AllArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class SgIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_USER_ID = "123456789";
    private static final String TEST_USERNAME = "testUser";

    @Autowired
    private SgProcessor processor;
    @Autowired
    private SgEventRepository repo;
    @Autowired
    private TelegramUserRepository userRepo;
    @SpyBean
    private TelegramService telegramService;
    @SpyBean
    private XpathScraper scraper;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MongoTemplate mongoTemplate;

    private static final Instant NOW = Instant.parse("2025-02-20T00:00:00.000Z");
    private static final MutableClock CLOCK = new MutableClock(NOW);
    private static final String EVENTS_DIR = "src/test/resources/web/stopgame/events/";
    private static final String EVENT_A_ID = "1451";

    @TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        public Clock clock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setup() {
        CLOCK.set(NOW);
        repo.deleteAll();
        userRepo.deleteAll();
        reset(telegramService);

        registerUser(TEST_USER_ID, TEST_USERNAME);

        doNothing().when(telegramService).sendPhotoMessage(any(), any(), any());
        doNothing().when(telegramService).sendMessage(any(), any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(InputSource.class)
    void testEventProcessor(InputSource input) throws IOException {
        if (input.dbSeedPath != null) {
            List<SgEventEntry> seed = TestUtils.readJsonListFile(input.dbSeedPath, mapper, SgEventEntry.class);
            // Insert keeps the seed's version, so that a seed can also serve as the expected state
            mongoTemplate.insertAll(seed);
        }

        givenPage(input.actualHtmlPath);

        processor.process();

        List<SgEventEntry> actualEvents = repo.findAll();
        List<SgEventEntry> expectedEvents = TestUtils.readJsonListFile(input.expectedDbPath, mapper, SgEventEntry.class);
        JsonAssertions.assertThatJson(actualEvents)
                .ignoringFields("createdAt", "updatedAt")
                .isEqualToJson(expectedEvents);

        List<MessageWrapper> expectedMsgs = TestUtils.readJsonListFile(input.expectedMessagesPath, mapper, MessageWrapper.class);
        JsonAssertions.assertThatJson(sentMessages())
                .isEqualToJson(expectedMsgs);
    }

    @Test
    void testEventLifecycleAcrossSeveralRuns() throws IOException {
        // Event a: scheduled -> live -> gone -> scheduled again, each site state processed twice
        List<String> pages = List.of("a-b-c.html", "a_live-b-c.html", "b-c.html", "a-b-c.html");
        List<List<String>> expectedHeaders = List.of(
                List.of("🆕 Новое событие", "🆕 Новое событие", "🆕 Новое событие"), List.of(),
                List.of("🔴 В эфире"), List.of(),
                List.of(), List.of(),
                List.of("🔁 Снова в расписании"), List.of());
        List<EventStatus> expectedStatuses = List.of(
                EventStatus.SCHEDULED, EventStatus.SCHEDULED,
                EventStatus.LIVE, EventStatus.LIVE,
                EventStatus.FINISHED, EventStatus.FINISHED,
                EventStatus.SCHEDULED, EventStatus.SCHEDULED);
        Instant scheduledDate = Instant.parse("2025-02-21T15:00:00Z");
        Instant liveAt = NOW.plus(Duration.ofHours(2));
        Instant goneAt = NOW.plus(Duration.ofHours(4));

        for (int run = 0; run < expectedHeaders.size(); run++) {
            String page = pages.get(run / 2);
            CLOCK.set(NOW.plus(Duration.ofHours(run)));
            clearInvocations(telegramService);
            givenPage(EVENTS_DIR + page);

            processor.process();

            String step = "run " + (run + 1) + " (" + page + ")";
            assertThat(sentMessages())
                    .as(step + ": messages")
                    .extracting(msg -> msg.message().lines().findFirst().orElseThrow())
                    .map(header -> header.startsWith("🔴 В эфире") ? "🔴 В эфире" : header)
                    .containsExactlyElementsOf(expectedHeaders.get(run));

            List<SgEventEntry> events = repo.findAll();
            assertThat(events).as(step + ": events").hasSize(3);
            SgEventEntry a = repo.findById(EVENT_A_ID).orElseThrow();
            assertThat(a.getStatus()).as(step + ": status").isEqualTo(expectedStatuses.get(run));
            assertThat(a.getDate()).as(step + ": date").isEqualTo(scheduledDate);
            assertThat(a.getStartedAt()).as(step + ": startedAt").isEqualTo(run < 2 ? null : liveAt);
            assertThat(a.getEndedAt()).as(step + ": endedAt").isEqualTo(run < 4 ? null : goneAt);
            // Saved on the first run of each state only
            assertThat(a.getVersion()).as(step + ": version").isEqualTo((long) run / 2);
            assertThat(events).as(step + ": other events are saved once")
                    .filteredOn(e -> !e.getId().equals(EVENT_A_ID))
                    .extracting(SgEventEntry::getVersion)
                    .containsOnly(0L);
        }
    }

    @Test
    void testEachMessageIsSentToEveryUser() throws IOException {
        String secondUserId = "987654321";
        registerUser(secondUserId, "secondUser");
        givenPage(EVENTS_DIR + "a-b-c.html");

        processor.process();

        List<MessageWrapper> expectedMsgs = TestUtils.readJsonListFile(EVENTS_DIR + "a-b-c-msg.json", mapper, MessageWrapper.class);
        List<MessageWrapper> sent = sentMessages();
        assertThat(sent).hasSize(expectedMsgs.size() * 2);
        for (MessageWrapper expected : expectedMsgs) {
            assertThat(sent)
                    .filteredOn(msg -> msg.message().equals(expected.message()))
                    .extracting(MessageWrapper::chatId)
                    .containsExactlyInAnyOrder(TEST_USER_ID, secondUserId);
        }
    }

    private void registerUser(String id, String username) {
        userRepo.save(TelegramUser.builder()
                .id(id)
                .username(username)
                .targetChatId(id)
                .build());
    }

    private void givenPage(String htmlPath) throws IOException {
        Document doc = Jsoup.parse(Path.of(htmlPath).toFile(), "UTF-8");
        doReturn(doc).when(scraper).parseDocument(any());
    }

    /**
     * Messages sent since the last {@code reset} / {@code clearInvocations} of the Telegram service
     */
    private List<MessageWrapper> sentMessages() {
        ArgumentCaptor<String> chatIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramService, atLeast(0))
                .sendPhotoMessage(chatIdCaptor.capture(), imageUrlCaptor.capture(), messageCaptor.capture());

        List<String> actualChatIds = chatIdCaptor.getAllValues();
        List<String> actualImageUrls = imageUrlCaptor.getAllValues();
        List<String> actualMessages = messageCaptor.getAllValues();
        List<MessageWrapper> actualMsgs = new ArrayList<>();
        for (int i = 0; i < actualChatIds.size(); i++) {
            actualMsgs.add(new MessageWrapper(actualChatIds.get(i), actualImageUrls.get(i), actualMessages.get(i)));
        }
        return actualMsgs;
    }

    //TODO Добавить кейсы:

    //9. Пользователь не найден/отсутствует
    //Проверка, что не происходит отправка сообщений, если нет пользователя в базе.

    //10. Ошибка парсинга/некорректный HTML
    //Проверка устойчивости к ошибкам парсинга.

    //11. Изменение и удаление картинки

    @AllArgsConstructor
    enum InputSource {
        NO_EVENTS(
                "No events on the site, empty database, no messages should be sent",
                null,
                "src/test/resources/web/stopgame/events/no-events.html",
                null,
                null),
        NEW_SCHEDULED_EVENTS(
                "Three events are present on the site, the database is empty, all events should be added and messages sent",
                null,
                "src/test/resources/web/stopgame/events/a-b-c.html",
                "src/test/resources/web/stopgame/events/a-b-c-db-expected.json",
                "src/test/resources/web/stopgame/events/a-b-c-msg.json"),
        NEW_LIVE_AND_SCHEDULED_EVENTS(
                "A new live event and two regular events appear on the site, the database is empty. All should be added and messages sent",
                null,
                "src/test/resources/web/stopgame/events/a_live-b-c.html",
                "src/test/resources/web/stopgame/events/a_live-b-c-db-expected.json",
                "src/test/resources/web/stopgame/events/a_live-b-c-msg.json"),
        NO_CHANGES(
                "No changes between the site and the database, no messages should be sent",
                "src/test/resources/web/stopgame/events/a_live-b-c-seed.json",
                "src/test/resources/web/stopgame/events/a_live-b-c.html",
                "src/test/resources/web/stopgame/events/a_live-b-c-seed.json",
                null),
        CANCELED_EVENT(
                "A canceled event exists in the database, but not on the site. The database should be updated and a cancellation message sent",
                "src/test/resources/web/stopgame/events/a-b-c-seed.json",
                "src/test/resources/web/stopgame/events/b.html",
                "src/test/resources/web/stopgame/events/a_canceled-b-c_canceled-db-expected.json",
                "src/test/resources/web/stopgame/events/a_canceled-c_canceled-msg.json"),
        CHANGED_EVENTS(
                "Two events in the database are present on the site with updated data (date and description changed). The database should be updated, and notifications about the changes should be sent",
                "src/test/resources/web/stopgame/events/a-b-c-seed.json",
                "src/test/resources/web/stopgame/events/a-b_new_date-c_new_desc.html",
                "src/test/resources/web/stopgame/events/a-b_new_date-c_new_desc-db-expected.json",
                "src/test/resources/web/stopgame/events/b_new_date-c_new_desc-msg.json"),
        PARTICIPANTS_CHANGED(
                "An event in the database is on the site and only its participants changed. The database should be updated and an update message with the participants change sent",
                "src/test/resources/web/stopgame/events/a-b_old_participants-c-seed.json",
                "src/test/resources/web/stopgame/events/a-b-c.html",
                "src/test/resources/web/stopgame/events/a-b_old_participants-c-db-expected.json",
                "src/test/resources/web/stopgame/events/b_old_participants-msg.json"),
        B_FINISHED(
                "A LIVE event disappears from the site. It should become FINISHED with its date kept and endedAt set, and no message should be sent",
                "src/test/resources/web/stopgame/events/a_live-b-c-seed.json",
                "src/test/resources/web/stopgame/events/b-c.html",
                "src/test/resources/web/stopgame/events/a_finished-b-c-db-expected.json",
                null),
        CANCELED_EVENT_RETURNS(
                "A CANCELED event is on the site again, not live and unchanged. It should become SCHEDULED and a return message with the card sent",
                "src/test/resources/web/stopgame/events/a_canceled-b-c-seed.json",
                "src/test/resources/web/stopgame/events/a-b-c.html",
                "src/test/resources/web/stopgame/events/a_canceled_returned-b-c-db-expected.json",
                "src/test/resources/web/stopgame/events/a_canceled_returned-msg.json"),
        FINISHED_EVENT_RETURNS_WITH_NEW_DATE(
                "A FINISHED event is on the site again with a new date. It should become SCHEDULED with the new date, keep its stream timing, and a return message with the date change sent",
                "src/test/resources/web/stopgame/events/a_finished_old_date-b-c-seed.json",
                "src/test/resources/web/stopgame/events/a-b-c.html",
                "src/test/resources/web/stopgame/events/a_finished_returned_new_date-b-c-db-expected.json",
                "src/test/resources/web/stopgame/events/a_finished_returned_new_date-msg.json"),
        CANCELED_EVENT_RETURNS_LIVE(
                "A CANCELED event is on the site again and live. It should become LIVE with its date kept and startedAt set, and a live message sent",
                "src/test/resources/web/stopgame/events/a_canceled-b-c-seed.json",
                "src/test/resources/web/stopgame/events/a_live-b-c.html",
                "src/test/resources/web/stopgame/events/a_canceled_returned_live-b-c-db-expected.json",
                "src/test/resources/web/stopgame/events/a_canceled_returned_live-msg.json"),
        PAST_SCHEDULED_EVENT_DISAPPEARS(
                "A SCHEDULED event with a past date disappears from the site. It should become FINISHED without stream timing, and no message should be sent",
                "src/test/resources/web/stopgame/events/a_past_date-b-c-seed.json",
                "src/test/resources/web/stopgame/events/b-c.html",
                "src/test/resources/web/stopgame/events/a_past_date_finished-b-c-db-expected.json",
                null),
        MASS_CHANGES(
                "In one run a LIVE event disappears, an event changes, a new event appears and a scheduled event is canceled. Each gets its own message, the finished one gets none",
                "src/test/resources/web/stopgame/events/a_live-b_old_desc-d-seed.json",
                "src/test/resources/web/stopgame/events/b-c.html",
                "src/test/resources/web/stopgame/events/a_finished-b-c-d_canceled-db-expected.json",
                "src/test/resources/web/stopgame/events/b_new_desc-c-d_canceled-msg.json")
        ;

        private final String description;
        private final String dbSeedPath;
        private final String actualHtmlPath;
        private final String expectedDbPath;
        private final String expectedMessagesPath;

        @Override
        public String toString() {
            return name() + " - " + description;
        }
    }

    public record MessageWrapper(String chatId, String imageUrl, String message) {
    }
}