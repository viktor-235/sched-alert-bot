package com.github.viktor235.schedalertbot.config;

import com.github.viktor235.schedalertbot.web.ScraperException;
import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.file.AccessDeniedException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SchedulerErrorHandlerTest {

    private final SchedulerErrorHandler handler = new SchedulerErrorHandler();

    @Test
    void handleError_whenHttpConnectTimeoutIsNested_thenSingleWarnLine(CapturedOutput output) {
        var connectTimeout = new HttpConnectTimeoutException("HTTP connect timed out");
        connectTimeout.initCause(new ConnectException("HTTP connect timed out"));
        var error = new ScraperException("Error while parsing page: HTTP connect timed out",
                new ScraperException("Error while accessing document: HTTP connect timed out", connectTimeout));

        handler.handleError(error);

        assertThat(output.getOut())
                .contains("WARN")
                .contains("Transient error in scheduled task: Error while parsing page: HTTP connect timed out"
                        + " (ConnectException: HTTP connect timed out)")
                .doesNotContain("ERROR")
                .doesNotContain("\tat ");
    }

    @Test
    void handleError_whenServerUnavailable_thenWarnWithStatus(CapturedOutput output) {
        handler.handleError(new ScraperException("Error while parsing page", httpStatus(503)));

        assertThat(output.getOut())
                .contains("WARN")
                .contains("(HttpStatusException: HTTP error fetching URL. Status=503, URL=[https://example.com])")
                .doesNotContain("\tat ");
    }

    @Test
    void handleError_whenOtherError_thenErrorWithStackTrace(CapturedOutput output) {
        handler.handleError(new ScraperException("XPath is broken", new IllegalStateException("boom")));

        assertThat(output.getOut())
                .contains("ERROR")
                .contains("XPath is broken")
                .contains("\tat ");
    }

    @ParameterizedTest
    @MethodSource("networkFailures")
    void isTransient_whenNetworkFailure_thenTrue(Throwable error) {
        assertThat(SchedulerErrorHandler.isTransient(error)).isTrue();
    }

    static Stream<Throwable> networkFailures() {
        return Stream.of(
                new ScraperException("Read timed out", new SocketTimeoutException("Read timed out")),
                new SocketException("Connection reset"),
                new ConnectException("Connection refused"),
                new IOException("EOF reached while reading", new EOFException("EOF reached while reading")));
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void isTransient_whenRetryableStatus_thenTrue(int status) {
        assertThat(SchedulerErrorHandler.isTransient(httpStatus(status))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410})
    void isTransient_whenPermanentStatus_thenFalse(int status) {
        assertThat(SchedulerErrorHandler.isTransient(httpStatus(status))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void isTransient_whenRetryCannotFixIt_thenFalse(Throwable error) {
        assertThat(SchedulerErrorHandler.isTransient(error)).isFalse();
    }

    static Stream<Throwable> permanentFailures() {
        return Stream.of(
                new UnknownHostException("stopgame.ru"),
                new UncheckedIOException("Failed to write calendar", new AccessDeniedException("streams.ics")),
                new IOException("Disk full"));
    }

    @Test
    void describe_whenNoCause_thenClassAndMessage() {
        assertThat(SchedulerErrorHandler.describe(new ConnectException("Connection refused")))
                .isEqualTo("ConnectException: Connection refused");
    }

    private static HttpStatusException httpStatus(int status) {
        return new HttpStatusException("HTTP error fetching URL", status, "https://example.com");
    }
}
