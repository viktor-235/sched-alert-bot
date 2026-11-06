package com.github.viktor235.schedalertbot.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlUtilsTest {

    private static final String URL = "https://images.stopgame.ru/streams/2026/10/25/c583x325/JgD7ZmJOk0aYLWws6rTs7g/poster.jpg";

    @Test
    void equalsIgnoringQuery_whenOnlyQueryDiffers_thenEqual() {
        assertThat(UrlUtils.equalsIgnoringQuery(URL + "?1792927808", URL + "?1792938608")).isTrue();
    }

    @Test
    void equalsIgnoringQuery_whenOneHasNoQuery_thenEqual() {
        assertThat(UrlUtils.equalsIgnoringQuery(URL, URL + "?1792938608")).isTrue();
    }

    @Test
    void equalsIgnoringQuery_whenFragmentDiffers_thenEqual() {
        assertThat(UrlUtils.equalsIgnoringQuery(URL + "#a", URL + "?1#b")).isTrue();
    }

    @Test
    void equalsIgnoringQuery_whenPathDiffers_thenNotEqual() {
        assertThat(UrlUtils.equalsIgnoringQuery(URL + "?1", URL.replace("poster.jpg", "other.jpg") + "?1")).isFalse();
    }

    @Test
    void equalsIgnoringQuery_whenBothNull_thenEqual() {
        assertThat(UrlUtils.equalsIgnoringQuery(null, null)).isTrue();
    }

    @Test
    void equalsIgnoringQuery_whenOneNull_thenNotEqual() {
        assertThat(UrlUtils.equalsIgnoringQuery(null, URL)).isFalse();
        assertThat(UrlUtils.equalsIgnoringQuery(URL, null)).isFalse();
    }
}
