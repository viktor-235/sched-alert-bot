package com.github.viktor235.schedalertbot.config;

import com.github.viktor235.schedalertbot.web.ScraperException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;

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
                .contains("Network timeout in scheduled task: Error while parsing page: HTTP connect timed out")
                .doesNotContain("ERROR")
                .doesNotContain("\tat ");
    }

    @Test
    void handleError_whenSocketTimeout_thenWarn(CapturedOutput output) {
        handler.handleError(new ScraperException("Read timed out", new SocketTimeoutException("Read timed out")));

        assertThat(output.getOut())
                .contains("WARN")
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

    @Test
    void isNetworkTimeout_whenConnectionRefused_thenFalse() {
        assertThat(SchedulerErrorHandler.isNetworkTimeout(new ConnectException("Connection refused"))).isFalse();
    }
}
