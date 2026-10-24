package com.github.viktor235.schedalertbot.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcsExportPropertiesTest {

    private static final String PREFIX = "site.test.ics";

    @Test
    void bind_whenSectionMissing_thenDisabledWithDefaultPastPeriod() {
        IcsExportProperties props = bind(Map.of());

        assertThat(props.enabled()).isFalse();
        assertThat(props.pastPeriod()).isEqualTo(Period.ofYears(3));
        assertThat(props.timeZone()).isNull();
    }

    @Test
    void bind_whenAllSet_thenDurationsParsed() {
        IcsExportProperties props = bind(Map.of(
                PREFIX + ".enabled", "true",
                PREFIX + ".path", "./ics/test.ics",
                PREFIX + ".default-event-duration", "120m",
                PREFIX + ".past-period", "1y",
                PREFIX + ".url", "https://www.twitch.tv/stopgameru",
                PREFIX + ".time-zone", "Europe/Moscow"));

        assertThat(props.enabled()).isTrue();
        assertThat(props.timeZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
        assertThat(props.path().normalize()).isEqualTo(Path.of("./ics/test.ics").normalize());
        assertThat(props.defaultEventDuration()).isEqualTo(Duration.ofMinutes(120));
        assertThat(props.pastPeriod()).isEqualTo(Period.ofYears(1));
        assertThat(props.url()).isEqualTo("https://www.twitch.tv/stopgameru");
    }

    @Test
    void bind_whenEnabledWithoutPastPeriod_thenThreeYears() {
        IcsExportProperties props = bind(Map.of(
                PREFIX + ".enabled", "true",
                PREFIX + ".path", "./ics/test.ics",
                PREFIX + ".default-event-duration", "120m",
                PREFIX + ".url", "https://example.com"));

        assertThat(props.pastPeriod()).isEqualTo(Period.ofYears(3));
    }

    @Test
    void bind_whenDisabledWithoutRequiredSettings_thenNoError() {
        IcsExportProperties props = bind(Map.of(PREFIX + ".enabled", "false"));

        assertThat(props.enabled()).isFalse();
        assertThat(props.path()).isNull();
    }

    @Test
    void bind_whenEnabledWithMissingSettings_thenFailsNamingThem() {
        assertThatThrownBy(() -> bind(Map.of(
                PREFIX + ".enabled", "true",
                PREFIX + ".default-event-duration", "120m")))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("path")
                .hasMessageContaining("url")
                .hasMessageNotContaining("default-event-duration");
    }

    @Test
    void bind_whenUnknownTimeZone_thenFails() {
        assertThatThrownBy(() -> bind(Map.of(PREFIX + ".time-zone", "Europe/Nowhere")))
                .isInstanceOf(BindException.class);
    }

    private static IcsExportProperties bind(Map<String, String> props) {
        return IcsExportProperties.bind(new Binder(new MapConfigurationPropertySource(props)), PREFIX);
    }
}
