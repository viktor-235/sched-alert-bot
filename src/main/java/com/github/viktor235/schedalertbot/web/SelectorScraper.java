package com.github.viktor235.schedalertbot.web;

import com.github.viktor235.schedalertbot.utils.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
public class SelectorScraper {

    public Elements parsePage(String url, String selector) {
        try {
            Document doc = Jsoup.connect(url).get();
            return doc.select(selector);
        } catch (IOException e) {
            throw new AppException("Error while parsing page '%s': %s".formatted(url, e.getMessage()), e);
        }
    }

    public Optional<Element> getFirstElement(Element el, String selector) {
        return el.select(selector).stream()
                .findFirst();
    }

    public String getString(Element el, String selector) {
        String text = el.select(selector).text();
        log.debug("Selector '{}', text '{}'", selector, text);
        return text;
    }

    public List<String> getStrings(Element el, String selector) {
        List<String> txts = Stream.of(el.select(selector))
                .flatMap(Collection::stream)
                .map(Element::text)
                .toList();
        log.debug("Selector '{}', texts: {}", selector, txts);
        return txts;
    }
}
