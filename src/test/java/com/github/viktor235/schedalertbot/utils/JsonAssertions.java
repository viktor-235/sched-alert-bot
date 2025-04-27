package com.github.viktor235.schedalertbot.utils;

/**
 * A utility class that provides an entry point for creating assertions for JSON objects.
 * This class allows using custom assertions for comparing JSON objects with support for
 * ignoring fields.
 */
public class JsonAssertions {

    /**
     * Creates a new JsonAssert instance for the given actual object.
     *
     * @param actual the actual object to compare.
     * @return a JsonAssert instance to perform assertions on the actual object.
     */
    public static JsonAssert assertThatJson(Object actual) {
        return new JsonAssert(actual);
    }
}