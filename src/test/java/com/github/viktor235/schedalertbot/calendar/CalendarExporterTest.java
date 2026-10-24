package com.github.viktor235.schedalertbot.calendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalendarExporterTest {

    private static final Instant NOW = Instant.parse("2025-02-20T00:00:00Z");
    // 3 years before NOW in UTC calendar terms
    private static final Instant CUTOFF = Instant.parse("2022-02-20T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void export_whenEventHasNoEnd_thenDefaultDurationApplied() throws IOException {
        Path file = tempDir.resolve("site.ics");

        exporter(file).export(List.of(event("1", Instant.parse("2025-02-21T15:00:00Z"), null)));

        assertThat(read(file))
                .contains("DTSTART:20250221T150000Z\r\n")
                .contains("DTEND:20250221T170000Z\r\n");
    }

    @Test
    void export_whenEventHasEnd_thenEndKept() throws IOException {
        Path file = tempDir.resolve("site.ics");

        exporter(file).export(List.of(event("1", Instant.parse("2025-02-21T15:10:00Z"), Instant.parse("2025-02-21T18:05:00Z"))));

        assertThat(read(file))
                .contains("DTSTART:20250221T151000Z\r\n")
                .contains("DTEND:20250221T180500Z\r\n");
    }

    @Test
    void export_whenEventsAroundPastPeriod_thenOnlyEventsFromCutoffIncluded() throws IOException {
        Path file = tempDir.resolve("site.ics");

        exporter(file).export(List.of(
                event("at-cutoff", CUTOFF, null),
                event("before-cutoff", CUTOFF.minusSeconds(1), null),
                event("far-future", Instant.parse("2027-02-20T00:00:00Z"), null)));

        assertThat(uids(read(file))).containsExactly("at-cutoff", "far-future");
    }

    @Test
    void export_whenInputUnordered_thenSortedByStartThenId() throws IOException {
        Path file = tempDir.resolve("site.ics");
        Instant start = Instant.parse("2025-02-21T15:00:00Z");

        exporter(file).export(List.of(
                event("3", start.plusSeconds(60), null),
                event("2", start, null),
                event("1", start, null)));

        assertThat(uids(read(file))).containsExactly("1", "2", "3");
    }

    @Test
    void export_whenDirectoriesMissing_thenCreated() throws IOException {
        Path file = tempDir.resolve("a/b/site.ics");

        exporter(file).export(List.of());

        assertThat(read(file)).startsWith("BEGIN:VCALENDAR\r\n").doesNotContain("BEGIN:VEVENT");
    }

    @Test
    void export_whenFileExists_thenReplacedWithoutLeftovers() throws IOException {
        Path file = tempDir.resolve("site.ics");
        Files.writeString(file, "old content");
        CalendarExporter exporter = exporter(file);
        assertThat(exporter.fileExists()).isTrue();

        exporter.export(List.of(event("1", Instant.parse("2025-02-21T15:00:00Z"), null)));

        assertThat(read(file)).contains("UID:1@site.sched-alert-bot");
        try (var files = Files.list(tempDir)) {
            assertThat(files).containsExactly(file);
        }
    }

    @Test
    void export_whenPosixFileSystem_thenFileReadableByOthers() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path file = tempDir.resolve("site.ics");

        exporter(file).export(List.of());

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-r--r--");
    }

    @Test
    void fileExists_whenNoFile_thenFalse() {
        assertThat(exporter(tempDir.resolve("site.ics")).fileExists()).isFalse();
    }

    private static CalendarExporter exporter(Path file) {
        IcsExportProperties props = new IcsExportProperties(true, file, Duration.ofMinutes(120), Period.ofYears(3), "https://example.com", null, null);
        return new CalendarExporter("site", props, new IcsWriter(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CalendarEvent event(String id, Instant start, Instant end) {
        return new CalendarEvent(id, start, end, "Event " + id, null, null, null, 0, NOW);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static List<String> uids(String ics) {
        Matcher m = Pattern.compile("UID:(.+)@site\\.sched-alert-bot").matcher(ics);
        return m.results().map(r -> r.group(1)).toList();
    }
}
