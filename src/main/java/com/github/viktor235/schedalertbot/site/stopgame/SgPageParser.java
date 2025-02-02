package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.site.stopgame.model.SgEvent;
import com.github.viktor235.schedalertbot.utils.exception.AppException;
import com.github.viktor235.schedalertbot.web.SelectorPageParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

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

@Component
@RequiredArgsConstructor
public class SgPageParser {

    private final SelectorPageParser parser;
    private final Locale ruLocale = Locale.forLanguageTag("ru-RU");
    ZoneId zone = ZoneId.of("Europe/Moscow");
    DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM/HH.mm")
            .parseDefaulting(ChronoField.YEAR, LocalDateTime.now(zone).getYear())
            .toFormatter(ruLocale);

    public static final String URL = "https://stopgame.ru/live_schedule";
    public static final String EVENT_SELECTOR = "div[data-key]:has(div._stream_11xp0_102)";
    private String idSelector = "[data-key]";//todo
    private String nameSelector = "._stream-title_11xp0_1";
    private String dateSelector = "._stream-info_11xp0_1 > div:nth-child(1) > span._stream-info__value_11xp0_1";
    //    private String dayOfWeekSelector = "._stream-info_11xp0_1 > div:nth-child(1) > span._stream-info__subtitle_11xp0_1";
    private String timeSelector = "._stream-info_11xp0_1 > div:nth-child(2) > span._stream-info__value_11xp0_1";
    private String descriptionSelector = "._stream-description_11xp0_1";
    private String participantsSelector = "._user-info__name_dhept_1165";
    //todo move all url and selectors to db config for runtime control

    public List<SgEvent> parse() {
        return Stream.of(parser.parsePage(URL, EVENT_SELECTOR))
                .flatMap(Collection::stream)
                .map(this::parseStreamElement)
                .toList();
    }

    private SgEvent parseStreamElement(Element el) {
        boolean nowLive = "в эфире".equals(parser.getString(el, dateSelector).trim().toLowerCase(ruLocale));
        return SgEvent.builder()
                .id(extractId(el))
                .name(parser.getString(el, nameSelector))
                .date(nowLive ? null : extractDate(el))
                .description(parser.getString(el, descriptionSelector))
                .participants(parser.getStrings(el, participantsSelector))
                .nowLive(nowLive)
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
    private Instant extractDate(Element el) {
        LocalDateTime now = LocalDateTime.now(zone);
        String dateTime = parser.getString(el, dateSelector) + "/" + parser.getString(el, timeSelector);
        LocalDateTime eventDate = LocalDateTime.parse(dateTime, dateTimeFormatter);

        // Calc correct year
        eventDate = eventDate.withYear(now.getYear());
        if (eventDate.isBefore(now.minusMonths(2))) {
            eventDate = eventDate.plusYears(1);
        }
        return eventDate.atZone(zone).toInstant();
    }

    private String extractId(Element el) {
        return parser.getFirstElement(el, idSelector)
                .orElseThrow(() -> new AppException("Unable to extract event id"))
                .attr("data-key");
    }
}
