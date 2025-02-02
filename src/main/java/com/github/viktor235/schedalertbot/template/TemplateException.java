package com.github.viktor235.schedalertbot.template;

import com.github.viktor235.schedalertbot.utils.exception.AppException;

public class TemplateException extends AppException {

    public TemplateException(String message) {
        super(message);
    }

    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateException(Throwable cause) {
        super(cause);
    }
}
