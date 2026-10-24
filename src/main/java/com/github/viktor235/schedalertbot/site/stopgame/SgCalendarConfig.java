package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.calendar.CalendarExporter;
import com.github.viktor235.schedalertbot.calendar.IcsExportProperties;
import com.github.viktor235.schedalertbot.calendar.IcsWriter;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;

@Configuration
public class SgCalendarConfig {

    public static final String SITE_KEY = "stopgame";

    @Bean
    public IcsExportProperties sgIcsExportProperties(Environment env) {
        return IcsExportProperties.bind(Binder.get(env), "site.stopgame.ics");
    }

    @Bean
    public CalendarExporter sgCalendarExporter(IcsExportProperties sgIcsExportProperties, IcsWriter icsWriter, Clock clock) {
        return new CalendarExporter(SITE_KEY, sgIcsExportProperties, icsWriter, clock);
    }
}
