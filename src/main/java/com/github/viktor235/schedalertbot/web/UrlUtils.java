package com.github.viktor235.schedalertbot.web;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public final class UrlUtils {

    private UrlUtils() {
    }

    /**
     * Compares two URLs without their query string and fragment.
     * Splits the string instead of parsing a {@link java.net.URI}, so that unencoded characters cannot fail the comparison.
     */
    public static boolean equalsIgnoringQuery(String url1, String url2) {
        return Objects.equals(withoutQuery(url1), withoutQuery(url2));
    }

    private static String withoutQuery(String url) {
        return StringUtils.substringBefore(StringUtils.substringBefore(url, "?"), "#");
    }
}
