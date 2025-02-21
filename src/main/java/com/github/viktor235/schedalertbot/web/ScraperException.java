package com.github.viktor235.schedalertbot.web;

import com.github.viktor235.schedalertbot.utils.exception.AppException;

public class ScraperException extends AppException {

    public ScraperException(String message) {
        super(message);
    }

    public ScraperException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScraperException(Throwable cause) {
        super(cause);
    }
}
