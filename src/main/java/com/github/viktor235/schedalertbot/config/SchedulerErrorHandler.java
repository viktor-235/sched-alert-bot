package com.github.viktor235.schedalertbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.ErrorHandler;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Logs network timeouts of scheduled tasks as a single WARN line: they are transient
 * and the next run retries anyway. Any other error is logged with its stack trace.
 */
@Slf4j
public class SchedulerErrorHandler implements ErrorHandler {

    @Override
    public void handleError(Throwable t) {
        if (isNetworkTimeout(t)) {
            log.warn("Network timeout in scheduled task: {}", t.getMessage());
        } else {
            TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER.handleError(t);
        }
    }

    static boolean isNetworkTimeout(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
