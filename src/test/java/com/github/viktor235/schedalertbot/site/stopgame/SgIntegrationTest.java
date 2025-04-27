package com.github.viktor235.schedalertbot.site.stopgame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.viktor235.schedalertbot.AbstractIntegrationTest;
import com.github.viktor235.schedalertbot.TestUtils;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventRepository;
import com.github.viktor235.schedalertbot.telegram.TelegramService;
import com.github.viktor235.schedalertbot.telegram.TelegramUser;
import com.github.viktor235.schedalertbot.telegram.TelegramUserRepository;
import com.github.viktor235.schedalertbot.web.XpathScraper;
import lombok.AllArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    @TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        public Clock clock() {
            return Clock.fixed(Instant.parse("2025-02-20T00:00:00.000Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void setup() {
        repo.deleteAll();
        userRepo.deleteAll();
        reset(telegramService);

        TelegramUser user = TelegramUser.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .targetChatId(TEST_USER_ID)
                .build();
        userRepo.save(user);

        doNothing().when(telegramService).sendPhotoMessage(any(), any(), any());
        doNothing().when(telegramService).sendMessage(any(), any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(InputSource.class)
    void testEventProcessor(InputSource input) throws IOException {
        if (input.dbSeedPath != null) {
            List<SgEventEntry> seed = TestUtils.readJsonListFile(input.dbSeedPath, mapper, SgEventEntry.class);
            repo.saveAll(seed);
        }

        Document doc = Jsoup.parse(Path.of(input.actualHtmlPath).toFile(), "UTF-8");
        doReturn(doc).when(scraper).parseDocument(any());

        processor.process();

        List<SgEventEntry> actualEvents = repo.findAll();
        List<SgEventEntry> expectedEvents = TestUtils.readJsonListFile(input.expectedDbPath, mapper, SgEventEntry.class);
        assertThat(actualEvents).hasSize(expectedEvents.size())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdAt", "updatedAt")
                .isEqualTo(expectedEvents);

        List<ExpectedMessage> expectedMessages = TestUtils.readJsonListFile(input.expectedMessagesPath, mapper, ExpectedMessage.class);
        ArgumentCaptor<String> chatIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> imageUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramService, times(expectedMessages.size()))
                .sendPhotoMessage(chatIdCaptor.capture(), imageUrlCaptor.capture(), messageCaptor.capture());

        List<String> actualChatIds = chatIdCaptor.getAllValues();
        List<String> actualImageUrls = imageUrlCaptor.getAllValues();
        List<String> actualMessages = messageCaptor.getAllValues();
        for (int i = 0; i < expectedMessages.size(); i++) {
            assertThat(actualChatIds.get(i)).isEqualTo(expectedMessages.get(i).chatId());
            assertThat(actualImageUrls.get(i)).isEqualTo(expectedMessages.get(i).imageUrl());
            assertThat(actualMessages.get(i)).isEqualTo(expectedMessages.get(i).message());
        }
    }

    //TODO Добавить кейсы:

    //1. Обновление существующего события
    //В базе есть событие, на сайте оно же, но с изменёнными данными (например, изменилось время, статус, картинка, описание и т.д.).
    //Ожидание: событие обновляется в базе, отправляется сообщение об изменении.

    //2. Появление нового события при уже существующих
    //В базе есть события, на сайте появляется новое.
    //Ожидание: новое событие добавляется, отправляется сообщение только по нему.

    //3. Событие стало LIVE
    //В базе событие было обычным, на сайте оно стало LIVE.
    //Ожидание: обновление статуса, отправка сообщения о переходе в LIVE.

    //4. Событие стало FINISHED
    //В базе событие было LIVE или обычным, на сайте оно стало FINISHED.
    //Ожидание: обновление статуса, отправка сообщения о завершении.

    //5. Возврат CANCELED события

    //6. Возврат FINISHED события

    //7. Массовые изменения
    //Одновременно несколько событий обновились, появились новые, какие-то исчезли.
    //Ожидание: корректная обработка всех изменений, отправка соответствующих сообщений.

    //8. Пользователей несколько

    //9. Пользователь не найден/отсутствует
    //Проверка, что не происходит отправка сообщений, если нет пользователя в базе.

    //10.Ошибка парсинга/некорректный HTML
    //Проверка устойчивости к ошибкам парсинга.

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
                "src/test/resources/web/stopgame/events/a-b_live-c-msg.json"),
        NO_CHANGES(
                "No changes between the site and the database, no messages should be sent",
                "src/test/resources/web/stopgame/events/a-b_live-c-seed.json",
                "src/test/resources/web/stopgame/events/a_live-b-c.html",
                "src/test/resources/web/stopgame/events/a_live-b-c-db-expected.json",
                null),
        CANCELED_EVENT(
                "A canceled event exists in the database, but not on the site. The database should be updated and a cancellation message sent",
                "src/test/resources/web/stopgame/events/a-b-c-seed.json",
                "src/test/resources/web/stopgame/events/b.html",
                "src/test/resources/web/stopgame/events/a_canceled-b-c_canceled-db-expected.json",
                "src/test/resources/web/stopgame/events/a_canceled-c_canceled-msg.json"),
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

    public record ExpectedMessage(String chatId, String imageUrl, String message) {
    }
}