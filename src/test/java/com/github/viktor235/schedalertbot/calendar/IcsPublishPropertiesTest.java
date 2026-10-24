package com.github.viktor235.schedalertbot.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcsPublishPropertiesTest {

    private static final String PREFIX = "site.test.ics.publish";

    @Test
    void bind_whenSectionMissing_thenDisabledWithDefaults() {
        IcsPublishProperties props = bind(Map.of());

        assertThat(props.enabled()).isFalse();
        assertThat(props.branch()).isEqualTo("main");
        assertThat(props.minInterval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(props.apiUrl()).isEqualTo("https://api.github.com");
        assertThat(props.commitMessage()).isEqualTo("Update calendar");
    }

    @Test
    void bind_whenAllSet_thenParsed() {
        IcsPublishProperties props = bind(enabled(Map.of(
                PREFIX + ".branch", "gh-pages",
                PREFIX + ".min-interval", "1h",
                PREFIX + ".api-url", "https://github.example.com/api/v3",
                PREFIX + ".commit-message", "Update stopgame calendar")));

        assertThat(props.enabled()).isTrue();
        assertThat(props.repository()).isEqualTo("owner/calendars");
        assertThat(props.branch()).isEqualTo("gh-pages");
        assertThat(props.path()).isEqualTo("calendars/test.ics");
        assertThat(props.minInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(props.token()).isEqualTo("secret-token");
        assertThat(props.apiUrl()).isEqualTo("https://github.example.com/api/v3");
        assertThat(props.commitMessage()).isEqualTo("Update stopgame calendar");
    }

    @Test
    void bind_whenDisabledWithoutRequiredSettings_thenNoError() {
        IcsPublishProperties props = bind(Map.of(PREFIX + ".enabled", "false", PREFIX + ".token", ""));

        assertThat(props.enabled()).isFalse();
    }

    @Test
    void bind_whenEnabledWithMissingSettings_thenFailsNamingThem() {
        assertThatThrownBy(() -> bind(Map.of(
                PREFIX + ".enabled", "true",
                PREFIX + ".repository", "owner/calendars",
                PREFIX + ".token", " ")))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("path")
                .hasMessageContaining("token")
                .hasMessageNotContaining("repository");
    }

    @Test
    void bind_whenRepositoryWithoutOwner_thenFails() {
        assertThatThrownBy(() -> bind(enabled(Map.of(PREFIX + ".repository", "calendars"))))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("owner/name");
    }

    @Test
    void bind_whenAbsolutePath_thenFails() {
        assertThatThrownBy(() -> bind(enabled(Map.of(PREFIX + ".path", "/calendars/test.ics"))))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("relative");
    }

    @Test
    void toString_thenTokenMasked() {
        assertThat(bind(enabled(Map.of())).toString())
                .doesNotContain("secret-token")
                .contains("token=***");
    }

    private static Map<String, String> enabled(Map<String, String> overrides) {
        Map<String, String> props = new HashMap<>(Map.of(
                PREFIX + ".enabled", "true",
                PREFIX + ".repository", "owner/calendars",
                PREFIX + ".path", "calendars/test.ics",
                PREFIX + ".token", "secret-token"));
        props.putAll(overrides);
        return props;
    }

    private static IcsPublishProperties bind(Map<String, String> props) {
        return IcsPublishProperties.bind(new Binder(new MapConfigurationPropertySource(props)), PREFIX);
    }
}
