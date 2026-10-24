package com.github.viktor235.schedalertbot.calendar;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Publishes one site's calendar file to a GitHub repository when its content changes,
 * not more often than the configured interval. One instance per site.
 */
@Slf4j
public class CalendarPublisher {

    private final IcsPublishProperties props;
    private final Path file;
    private final GitHubContentsClient client;
    private final Clock clock;

    /**
     * Blob SHA of the published file as last seen, {@code null} if unknown
     */
    private String remoteSha;
    private boolean remoteKnown;
    /**
     * No publish attempt before this time
     */
    private Instant notBefore;

    CalendarPublisher(IcsExportProperties exportProps, IcsPublishProperties props, GitHubContentsClient client, Clock clock) {
        if (props.enabled() && !exportProps.enabled()) {
            throw new IllegalArgumentException("Calendar publishing is enabled, but the calendar export is disabled");
        }
        this.props = props;
        this.file = exportProps.path();
        this.client = client;
        this.clock = clock;
    }

    public static CalendarPublisher create(IcsExportProperties exportProps, IcsPublishProperties props, Clock clock) {
        GitHubContentsClient client = props.enabled() ? GitHubContentsClient.create(props) : null;
        return new CalendarPublisher(exportProps, props, client, clock);
    }

    public boolean isEnabled() {
        return props.enabled();
    }

    /**
     * Publishes the local calendar file if it differs from the published one and the minimum interval has passed.
     * A failure is only logged and retried after the interval.
     */
    public synchronized void publish() {
        if (!props.enabled()) {
            return;
        }
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            log.debug("Calendar {} does not exist yet, nothing to publish", file);
            return;
        } catch (IOException e) {
            log.error("Failed to read calendar {} for publishing", file, e);
            return;
        }
        String localSha = blobSha(content);
        if (remoteKnown && localSha.equals(remoteSha)) {
            return;
        }
        Instant now = clock.instant();
        if (notBefore != null && now.isBefore(notBefore)) {
            log.debug("Calendar {} changed, publishing postponed until {}", file, notBefore);
            return;
        }
        try {
            if (!remoteKnown) {
                remoteSha = client.getSha().orElse(null);
                remoteKnown = true;
                if (localSha.equals(remoteSha)) {
                    log.info("Calendar {} is already published to {}", file, client);
                    return;
                }
            }
            notBefore = now.plus(props.minInterval());
            try {
                remoteSha = client.put(content, remoteSha, props.commitMessage());
            } catch (GitHubConflictException e) {
                log.warn("Published calendar {} changed in the repository, retrying on its current version", client);
                remoteSha = client.getSha().orElse(null);
                if (localSha.equals(remoteSha)) {
                    return;
                }
                remoteSha = client.put(content, remoteSha, props.commitMessage());
            }
            log.info("Calendar {} published to {}", file, client);
        } catch (Exception e) {
            remoteKnown = false;
            remoteSha = null;
            notBefore = now.plus(props.minInterval());
            log.error("Failed to publish calendar {} to {}, next attempt not before {}", file, client, notBefore, e);
        }
    }

    /**
     * SHA-1 of the content as a git blob, the {@code sha} GitHub reports for a file
     */
    static String blobSha(byte[] content) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(sha1.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
