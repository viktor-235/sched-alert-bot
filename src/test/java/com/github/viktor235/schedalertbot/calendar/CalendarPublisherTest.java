package com.github.viktor235.schedalertbot.calendar;

import com.github.viktor235.schedalertbot.utils.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalendarPublisherTest {

    private static final Instant NOW = Instant.parse("2025-02-20T00:00:00Z");
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(15);
    private static final String MESSAGE = "Update calendar";

    @TempDir
    Path tempDir;

    private Path file;
    private MutableClock clock;
    private GitHubContentsClient client;
    private CalendarPublisher publisher;

    @BeforeEach
    void setup() {
        file = tempDir.resolve("site.ics");
        clock = new MutableClock(NOW);
        client = mock(GitHubContentsClient.class);
        publisher = publisher(true, true);
    }

    @Test
    void blobSha_thenMatchesGit() {
        // printf 'hello\n' | git hash-object --stdin
        assertThat(CalendarPublisher.blobSha("hello\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ce013625030ba8dba906f756967f9e9ca394464a");
        // git hash-object /dev/null
        assertThat(CalendarPublisher.blobSha(new byte[0]))
                .isEqualTo("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391");
    }

    @Test
    void publish_whenDisabled_thenNoCalls() throws IOException {
        write("v1");

        publisher(true, false).publish();

        verifyNoInteractions(client);
    }

    @Test
    void create_whenPublishingEnabledWithoutExport_thenFails() {
        assertThatThrownBy(() -> publisher(false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("export is disabled");
    }

    @Test
    void publish_whenNoLocalFile_thenNoCalls() {
        publisher.publish();

        verifyNoInteractions(client);
    }

    @Test
    void publish_whenRemoteEqualsLocalAfterStart_thenNoCommit() throws IOException {
        write("v1");
        when(client.getSha()).thenReturn(Optional.of(sha("v1")));

        publisher.publish();
        clock.advance(Duration.ofMinutes(1));
        publisher.publish();

        verify(client, times(1)).getSha();
        verify(client, never()).put(any(), any(), any());
    }

    @Test
    void publish_whenNoRemoteFile_thenCreated() throws IOException {
        write("v1");
        when(client.getSha()).thenReturn(Optional.empty());
        when(client.put(any(), isNull(), any())).thenReturn(sha("v1"));

        publisher.publish();

        verify(client).put(bytes("v1"), isNull(), eq(MESSAGE));
    }

    @Test
    void publish_whenChanged_thenCommittedOnceWithCachedSha() throws IOException {
        write("v2");
        when(client.getSha()).thenReturn(Optional.of(sha("v1")));
        when(client.put(any(), any(), any())).thenReturn(sha("v2"));

        publisher.publish();
        clock.advance(MIN_INTERVAL.multipliedBy(2));
        publisher.publish();

        verify(client).put(bytes("v2"), eq(sha("v1")), eq(MESSAGE));
        verify(client, times(1)).getSha();
        verifyNoMoreInteractions(client);
    }

    @Test
    void publish_whenChangedWithinInterval_thenLatestPublishedAfterInterval() throws IOException {
        write("v2");
        when(client.getSha()).thenReturn(Optional.of(sha("v1")));
        when(client.put(bytes("v2"), any(), any())).thenReturn(sha("v2"));
        when(client.put(bytes("v4"), any(), any())).thenReturn(sha("v4"));
        publisher.publish();

        clock.advance(Duration.ofMinutes(5));
        write("v3");
        publisher.publish();
        clock.advance(Duration.ofMinutes(5));
        write("v4");
        publisher.publish();
        verify(client, times(1)).put(any(), any(), any());

        clock.set(NOW.plus(MIN_INTERVAL));
        publisher.publish();

        verify(client).put(bytes("v4"), eq(sha("v2")), eq(MESSAGE));
        verify(client, never()).put(bytes("v3"), any(), any());
    }

    @Test
    void publish_whenConflict_thenRereadsAndRetriesOnce() throws IOException {
        write("v2");
        when(client.getSha()).thenReturn(Optional.of(sha("v1")), Optional.of("edited"));
        when(client.put(any(), eq(sha("v1")), any())).thenThrow(new GitHubConflictException("conflict", null));
        when(client.put(any(), eq("edited"), any())).thenReturn(sha("v2"));

        publisher.publish();

        verify(client).put(bytes("v2"), eq("edited"), eq(MESSAGE));
        verify(client, times(2)).put(any(), any(), any());
    }

    @Test
    void publish_whenConflictAndAlreadyPublished_thenNoRetry() throws IOException {
        write("v2");
        when(client.getSha()).thenReturn(Optional.of(sha("v1")), Optional.of(sha("v2")));
        when(client.put(any(), any(), any())).thenThrow(new GitHubConflictException("conflict", null));

        publisher.publish();

        verify(client, times(1)).put(any(), any(), any());
    }

    @Test
    void publish_whenFails_thenNotThrownAndRetriedAfterInterval() throws IOException {
        write("v2");
        when(client.getSha()).thenReturn(Optional.of(sha("v1")));
        when(client.put(any(), any(), any()))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(sha("v2"));

        publisher.publish();
        clock.advance(MIN_INTERVAL.minusSeconds(1));
        publisher.publish();
        verify(client, times(1)).put(any(), any(), any());

        clock.advance(Duration.ofSeconds(1));
        publisher.publish();

        verify(client, times(2)).put(bytes("v2"), eq(sha("v1")), eq(MESSAGE));
        // The remote version is re-read after a failure
        verify(client, times(2)).getSha();
    }

    @Test
    void publish_whenReadFails_thenRetriedAfterInterval() throws IOException {
        write("v2");
        when(client.getSha())
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(Optional.of(sha("v1")));
        when(client.put(any(), any(), any())).thenReturn(sha("v2"));

        publisher.publish();
        clock.advance(Duration.ofMinutes(1));
        publisher.publish();
        verify(client, times(1)).getSha();

        clock.set(NOW.plus(MIN_INTERVAL));
        publisher.publish();

        verify(client).put(bytes("v2"), eq(sha("v1")), eq(MESSAGE));
    }

    private CalendarPublisher publisher(boolean exportEnabled, boolean publishEnabled) {
        IcsExportProperties exportProps = new IcsExportProperties(exportEnabled, file, Duration.ofMinutes(120), Period.ofYears(3), "https://example.com", null, null);
        IcsPublishProperties publishProps = new IcsPublishProperties(publishEnabled, "owner/calendars", "main", "calendars/site.ics",
                MIN_INTERVAL, "token", "https://api.github.test", MESSAGE);
        return new CalendarPublisher(exportProps, publishProps, client, clock);
    }

    private void write(String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String content) {
        return aryEq(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha(String content) {
        return CalendarPublisher.blobSha(content.getBytes(StandardCharsets.UTF_8));
    }
}
