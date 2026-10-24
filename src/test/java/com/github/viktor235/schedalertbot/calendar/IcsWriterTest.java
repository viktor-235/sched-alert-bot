package com.github.viktor235.schedalertbot.calendar;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTest {

    private static final String GOLDEN_DIR = "src/test/resources/calendar/";
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final Instant UPDATED = Instant.parse("2025-02-20T10:00:00Z");

    private final IcsWriter writer = new IcsWriter();

    @Test
    void write_whenFullAndMinimalEvents_thenMatchesGoldenFile() throws IOException {
        CalendarEvent full = new CalendarEvent(
                "1451",
                Instant.parse("2025-02-21T15:00:00Z"),
                Instant.parse("2025-02-21T17:00:00Z"),
                "Ретрореквест. Fate/Stay Night; стрим восьмой, финал \\ эпилог",
                "Традиционное пятничное погружение в историю Святого грааля и Битвы семи магов, с длинным описанием.\n\nЯн Грибович, Иван Лоев",
                "https://www.twitch.tv/stopgameru",
                "https://images.stopgame.ru/streams/2025/02/20/c583x325/vG7OuFmDyRuIUXavLuGHwg/obRQj201m.jpg",
                3,
                UPDATED);
        CalendarEvent minimal = new CalendarEvent(
                "1452",
                Instant.parse("2025-02-22T18:00:00Z"),
                null,
                "Индиана. Sunless Sea",
                null,
                null,
                " ",
                0,
                UPDATED);

        byte[] actual = writer.write("stopgame", List.of(full, minimal));

        assertMatchesGolden(actual, "events.ics");
    }

    @Test
    void write_whenTimeZoneSet_thenLocalTimesWithTzidAndOneVTimeZone() throws IOException {
        CalendarEvent withEnd = new CalendarEvent("1451", Instant.parse("2025-02-21T17:00:00Z"),
                Instant.parse("2025-02-21T19:00:00Z"), "Event 1451", null, null, null, 1, UPDATED);
        CalendarEvent withoutEnd = new CalendarEvent("1452", Instant.parse("2025-02-22T18:00:00Z"),
                null, "Event 1452", null, null, null, 0, UPDATED);

        byte[] actual = writer.write("stopgame", MOSCOW, List.of(withEnd, withoutEnd));

        String text = new String(actual, StandardCharsets.UTF_8);
        assertThat(text).contains("DTSTART;TZID=Europe/Moscow:20250221T200000\r\n");
        assertThat(text).contains("DTSTAMP:20250220T100000Z\r\n", "LAST-MODIFIED:20250220T100000Z\r\n");
        assertThat(text.split("BEGIN:VTIMEZONE", -1)).hasSize(2);
        assertThat(text).contains("TZID:Europe/Moscow\r\n");
        assertMatchesGolden(actual, "events-moscow.ics");
    }

    @Test
    void write_whenMoscowAfter2014_thenPlusThree() {
        // +04:00 without the patched ZoneRulesBuilder (ical4j#903)
        CalendarEvent event = new CalendarEvent("1", Instant.parse("2026-10-25T12:00:00Z"), null, "Event 1",
                null, null, null, 0, UPDATED);

        String text = new String(writer.write("stopgame", MOSCOW, List.of(event)), StandardCharsets.UTF_8);

        assertThat(text).contains("DTSTART;TZID=Europe/Moscow:20261025T150000\r\n");
    }

    @Test
    void write_whenTimeZoneWithDaylightSaving_thenLocalTimesFollowIt() {
        CalendarEvent winter = new CalendarEvent("1", Instant.parse("2026-01-15T12:00:00Z"), null, "Winter",
                null, null, null, 0, UPDATED);
        CalendarEvent summer = new CalendarEvent("2", Instant.parse("2026-07-15T12:00:00Z"), null, "Summer",
                null, null, null, 0, UPDATED);

        String text = new String(writer.write("stopgame", ZoneId.of("Europe/Amsterdam"), List.of(winter, summer)),
                StandardCharsets.UTF_8);

        assertThat(text)
                .contains("DTSTART;TZID=Europe/Amsterdam:20260115T130000\r\n")
                .contains("DTSTART;TZID=Europe/Amsterdam:20260715T140000\r\n");
    }

    @Test
    void write_whenTimeZoneSetAndNoEvents_thenCalendarWithVTimeZoneOnly() {
        String text = new String(writer.write("stopgame", MOSCOW, List.of()), StandardCharsets.UTF_8);

        assertThat(text).contains("BEGIN:VTIMEZONE").doesNotContain("BEGIN:VEVENT");
    }

    @Test
    void write_whenTimeZoneSetAndCalledTwice_thenByteIdentical() {
        List<CalendarEvent> events = List.of(event("1"), event("2"));

        assertThat(writer.write("stopgame", MOSCOW, events)).isEqualTo(writer.write("stopgame", MOSCOW, events));
    }

    @Test
    void write_whenNameSet_thenNameAndXWrCalName() {
        String text = new String(writer.write("stopgame", "StopGame, стримы", null, List.of()), StandardCharsets.UTF_8);

        assertThat(text).contains("NAME:StopGame\\, стримы\r\n", "X-WR-CALNAME:StopGame\\, стримы\r\n");
    }

    @Test
    void write_whenNameBlank_thenNoName() {
        String text = new String(writer.write("stopgame", " ", null, List.of()), StandardCharsets.UTF_8);

        assertThat(text).doesNotContain("NAME");
    }

    @Test
    void write_whenNoEvents_thenEmptyCalendar() throws IOException {
        assertMatchesGolden(writer.write("stopgame", List.of()), "empty.ics");
    }

    @Test
    void write_whenCalledTwice_thenByteIdentical() throws InterruptedException {
        List<CalendarEvent> events = List.of(event("1"), event("2"));

        byte[] first = writer.write("stopgame", events);
        // A generated DTSTAMP of now would differ after a second
        Thread.sleep(1100);
        byte[] second = writer.write("stopgame", events);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void write_whenSameSourceIdOnDifferentSites_thenDifferentUids() {
        String a = new String(writer.write("stopgame", List.of(event("1"))), StandardCharsets.UTF_8);
        String b = new String(writer.write("othersite", List.of(event("1"))), StandardCharsets.UTF_8);

        assertThat(a).contains("UID:1@stopgame.sched-alert-bot\r\n");
        assertThat(b).contains("UID:1@othersite.sched-alert-bot\r\n");
    }

    private static CalendarEvent event(String id) {
        return new CalendarEvent(id, Instant.parse("2025-02-21T15:00:00Z"), Instant.parse("2025-02-21T17:00:00Z"),
                "Event " + id, null, null, null, 0, UPDATED);
    }

    private static void assertMatchesGolden(byte[] actual, String goldenName) throws IOException {
        Path golden = Path.of(GOLDEN_DIR + goldenName);
        assertThat(new String(actual, StandardCharsets.UTF_8))
                .isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    }
}
