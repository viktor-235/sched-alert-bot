package com.github.viktor235.schedalertbot.calendar;

/**
 * GitHub rejected a file update because it is not based on the current version of the file
 */
public class GitHubConflictException extends RuntimeException {

    public GitHubConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
