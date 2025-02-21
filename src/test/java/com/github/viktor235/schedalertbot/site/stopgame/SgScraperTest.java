package com.github.viktor235.schedalertbot.site.stopgame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.viktor235.schedalertbot.config.JacksonConfig;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEvent;
import com.github.viktor235.schedalertbot.web.SelectorScraper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(classes = {SgScraper.class, SelectorScraper.class, JacksonConfig.class})
class SgScraperTest {

    @SpyBean
    private SelectorScraper scraper;
    @Autowired
    private SgScraper sgScraper;
    @Autowired
    private ObjectMapper mapper;

    @ParameterizedTest
    @EnumSource(InputSource.class)
    void parse_whenValidElements_thenReturnsListOfEvents(InputSource inputSource) throws IOException {
        Document doc = Jsoup.parse(Path.of(inputSource.getActualHtmlPath()).toFile(), "UTF-8");
        doReturn(doc).when(scraper).parseDocument(any());
        List<SgEvent> expectedEvents = readJsonFile(inputSource.getExpectedJsonPath());

        List<SgEvent> events = sgScraper.parse();

        assertNotNull(events);
        assertEquals(expectedEvents.size(), events.size());
        assertIterableEquals(expectedEvents, events, () -> "Expected:\n" + expectedEvents + ", but was:\n" + events);
    }

    private List<SgEvent> readJsonFile(String jsonFilePath) throws IOException {
        return mapper.readValue(
                Files.readString(Path.of(jsonFilePath)),
                new TypeReference<>() {
                }
        );
    }

    @Getter
    @AllArgsConstructor
    enum InputSource {
        NO_EVENTS("src/test/resources/web/stopgame/no-events.html",
                "src/test/resources/web/stopgame/no-events.json"),
        THREE_EVENTS("src/test/resources/web/stopgame/three-events.html",
                "src/test/resources/web/stopgame/three-events.json"),
        LIVE_AND_TWO_EVENTS("src/test/resources/web/stopgame/live-and-two-events.html",
                "src/test/resources/web/stopgame/live-and-two-events.json");

        private final String actualHtmlPath;
        private final String expectedJsonPath;
    }
}
