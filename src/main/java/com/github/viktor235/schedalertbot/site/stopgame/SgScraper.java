package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.site.stopgame.model.SgEvent;
import com.github.viktor235.schedalertbot.web.XpathScraper;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SgScraper {

    private final XpathScraper scraper;

    @Value("${site.stopgame.scraper.url}")
    private String url;
    @Value("${site.stopgame.scraper.selector.event}")
    private String eventSelector;
    @Value("${site.stopgame.scraper.selector.id}")
    private String idSelector; //todo try xpath for attribute
    @Value("${site.stopgame.scraper.selector.name}")
    private String nameSelector;
    @Value("${site.stopgame.scraper.selector.date}")
    private String dateSelector;
    @Value("${site.stopgame.scraper.selector.time}")
    private String timeSelector;
    @Value("${site.stopgame.scraper.selector.description}")
    private String descriptionSelector;
    @Value("${site.stopgame.scraper.selector.participants}")
    private String participantsSelector;
    @Value("${site.stopgame.scraper.selector.imageUrl}")
    private String imageUrlSelector;

    private final Locale ruLocale = Locale.forLanguageTag("ru-RU");
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    private final DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM/HH.mm")
            .parseDefaulting(ChronoField.YEAR, LocalDateTime.now(zone).getYear())
            .toFormatter(ruLocale);

    public List<SgEvent> parse() {
        return Stream.of(scraper.parsePage(url, eventSelector))
                .flatMap(Collection::stream)
                .map(this::parseStreamElement)
                .toList();
    }

    SgEvent parseStreamElement(Element el) {
        boolean nowLive = "в эфире".equals(scraper.getString(el, dateSelector).trim().toLowerCase(ruLocale));
        return SgEvent.builder()
                .id(scraper.getString(el, idSelector)) //TODO check required fields
                .name(scraper.getString(el, nameSelector))
                .date(nowLive ? null : extractDate(el))
                .description(scraper.getString(el, descriptionSelector))
                .participants(scraper.getStrings(el, participantsSelector))
                .nowLive(nowLive)
                .imageUrl(scraper.getString(el, imageUrlSelector))
                .build();
    }

    /**
     * Extracts the date and time from the given HTML element, determines the correct year,
     * and returns the result as an {@link Instant}.
     * <p>
     * Parses the date and time from {@code el} using the configured selectors. Since the year
     * is not provided, it adjusts the parsed date to the current year or the next year if the
     * date is more than 2 months in the past.
     * </p>
     *
     * @param el the HTML element containing date without year and time
     * @return the extracted date and time as an {@link Instant}
     * @throws DateTimeParseException if parsing fails
     */
    Instant extractDate(Element el) {
        LocalDateTime now = LocalDateTime.now(zone);
        String dateTime = scraper.getString(el, dateSelector) + "/" + scraper.getString(el, timeSelector);
        LocalDateTime eventDate = LocalDateTime.parse(dateTime, dateTimeFormatter);

        // Calc correct year
        eventDate = eventDate.withYear(now.getYear());
        if (eventDate.isBefore(now.minusMonths(2))) {
            eventDate = eventDate.plusYears(1);
        }
        return eventDate.atZone(zone).toInstant();
    }
}
