package com.github.viktor235.schedalertbot.site.stopgame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.viktor235.schedalertbot.TestUtils;
import com.github.viktor235.schedalertbot.config.JacksonConfig;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventWeb;
import com.github.viktor235.schedalertbot.web.XpathScraper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(classes = {SgScraper.class, XpathScraper.class, JacksonConfig.class, SgScraperTest.FixedClockConfig.class})
class SgScraperTest {

    @SpyBean
    private XpathScraper scraper;
    @Autowired
    private SgScraper sgScraper;
    @Autowired
    private ObjectMapper mapper;

    @TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        public Clock clock() {
            return Clock.fixed(Instant.parse("2025-02-20T00:00:00.000Z"), ZoneOffset.UTC);
        }
    }

    @ParameterizedTest
    @EnumSource(InputSource.class)
    void parse_whenValidElements_thenReturnsListOfEvents(InputSource inputSource) throws IOException {
        Document doc = Jsoup.parse(Path.of(inputSource.getActualHtmlPath()).toFile(), "UTF-8");
        doReturn(doc).when(scraper).parseDocument(any());
        List<SgEventWeb> expectedEvents = TestUtils.readJsonListFile(inputSource.getExpectedJsonPath(), mapper, SgEventWeb.class);

        List<SgEventWeb> events = sgScraper.parse();

        assertNotNull(events);
        assertEquals(expectedEvents.size(), events.size());
        assertIterableEquals(expectedEvents, events, () -> "Expected:\n" + expectedEvents + ", but was:\n" + events);
    }

    @Getter
    @AllArgsConstructor
    enum InputSource {
        NO_EVENTS("src/test/resources/web/stopgame/events/no-events.html",
                null),
        THREE_EVENTS("src/test/resources/web/stopgame/events/a-b-c.html",
                "src/test/resources/web/stopgame/events/a-b-c-parsed.json"),
        LIVE_AND_TWO_EVENTS("src/test/resources/web/stopgame/events/a_live-b-c.html",
                "src/test/resources/web/stopgame/events/a_live-b-c-parsed.json");

        private final String actualHtmlPath;
        private final String expectedJsonPath;
    }
}
