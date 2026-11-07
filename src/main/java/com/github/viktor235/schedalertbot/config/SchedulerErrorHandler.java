package com.github.viktor235.schedalertbot.config;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.ErrorHandler;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Logs transient network errors of scheduled tasks (timeouts, dropped or refused connections,
 * HTTP 408/429/5xx) as a single WARN line: they pass by themselves and the next run retries anyway.
 * The root cause is kept in the line to tell e.g. a 503 from a timeout.
 * Any other error, including ones a retry cannot fix (HTTP 403/404, unknown host, TLS, file I/O),
 * is logged with its stack trace.
 */
@Slf4j
public class SchedulerErrorHandler implements ErrorHandler {

    @Override
    public void handleError(Throwable t) {
        if (isTransient(t)) {
            log.warn("Transient error in scheduled task: {}", describe(t));
        } else {
            TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER.handleError(t);
        }
    }

    /**
     * Whether the error or any of its causes is a network failure that may pass by itself on a retry
     */
    static boolean isTransient(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketException // connection reset or refused
                    || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof EOFException // connection closed by the server
                    || cause instanceof HttpStatusException e && isTransientStatus(e.getStatusCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Request timeout, rate limit or server error: the same request may succeed later.
     * Other 4xx statuses (403, 404) need a change on our side and do not pass by themselves.
     */
    private static boolean isTransientStatus(int status) {
        HttpStatusCode code = HttpStatusCode.valueOf(status);
        return code.is5xxServerError()
                || code.isSameCodeAs(HttpStatus.REQUEST_TIMEOUT)
                || code.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Message of the error with the class and message of its root cause
     */
    static String describe(Throwable t) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(t);
        if (root == t) {
            return root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        return "%s (%s: %s)".formatted(t.getMessage(), root.getClass().getSimpleName(), root.getMessage());
    }
}
