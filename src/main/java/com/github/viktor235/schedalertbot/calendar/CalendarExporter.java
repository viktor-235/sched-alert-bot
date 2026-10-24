package com.github.viktor235.schedalertbot.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Writes one site's calendar file from its events. One instance per site.
 */
@RequiredArgsConstructor
@Slf4j
public class CalendarExporter {

    private final String siteKey;
    private final IcsExportProperties props;
    private final IcsWriter writer;
    private final Clock clock;

    public boolean isEnabled() {
        return props.enabled();
    }

    public boolean fileExists() {
        return Files.exists(props.path());
    }

    /**
     * Earliest start of an event kept in the file: now minus the past period
     */
    public Instant cutoff() {
        return ZonedDateTime.now(clock)
                .withZoneSameInstant(ZoneOffset.UTC)
                .minus(props.pastPeriod())
                .toInstant();
    }

    /**
     * Replaces the calendar file with the given events: applies the default duration,
     * drops events older than the past period and sorts the rest
     */
    public void export(List<CalendarEvent> events) {
        Instant cutoff = cutoff();
        List<CalendarEvent> included = events.stream()
                .filter(e -> !e.start().isBefore(cutoff))
                .map(this::withEnd)
                .sorted(Comparator.comparing(CalendarEvent::start).thenComparing(CalendarEvent::sourceId))
                .toList();

        writeAtomically(props.path(), writer.write(siteKey, props.name(), props.timeZone(), included));
        log.info("Calendar {} written with {} events", props.path(), included.size());
    }

    private CalendarEvent withEnd(CalendarEvent e) {
        if (e.end() != null) {
            return e;
        }
        return new CalendarEvent(e.sourceId(), e.start(), e.start().plus(props.defaultEventDuration()),
                e.summary(), e.description(), e.url(), e.imageUrl(), e.sequence(), e.lastModified());
    }

    /**
     * Writes to a temporary file next to the target and moves it over the target,
     * so that a reader sees either the old or the new file, never a partial one
     */
    static void writeAtomically(Path target, byte[] content) {
        Path dir = target.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                // Temp files are owner-only; the calendar is meant to be read by others
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
            }
            Files.write(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write calendar " + target, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file {}", tmp, e);
                }
            }
        }
    }
}
