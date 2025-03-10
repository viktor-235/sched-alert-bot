package com.github.viktor235.schedalertbot.web;

import com.github.viktor235.schedalertbot.utils.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.seimicrawler.xpath.JXDocument;
import org.seimicrawler.xpath.JXNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class XpathScraper {

    public Document parseDocument(String url) {
        try {
            return Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new ScraperException("Error while accessing document '%s': %s".formatted(url, e.getMessage()), e);
        }
    }

    public Elements parsePage(String url, String xpath) {
        try {
            Document doc = parseDocument(url);
            return doc.selectXpath(xpath);
        } catch (Exception e) {
            throw new ScraperException("Error while parsing page '%s' with XPath '%s': %s"
                    .formatted(url, xpath, e.getMessage()), e);
        }
    }

    public String getString(Element el, String xpath) {
        String text = extractStrings(el, xpath).stream()
                .findFirst()
                .orElse("");
        log.debug("XPath '{}', text '{}'", xpath, text);
        return text;
    }

    public List<String> getStrings(Element el, String xpath) {
        List<String> txts = extractStrings(el, xpath);
        log.debug("XPath '{}', texts: {}", xpath, txts);
        return txts;
    }

    private static List<String> extractStrings(Element element, String xpath) {
        try {
            JXDocument doc = new JXDocument(new Elements(element));
            List<JXNode> nodes = doc.selN(xpath);

            return nodes.stream()
                    .map(node -> node.isElement()
                            ? node.asElement().text()
                            : node.toString())
                    .toList();
        } catch (Exception e) {
            throw new ScraperException("Error while extracting by XPath '%s': %s"
                    .formatted(xpath, e.getMessage()), e);
        }
    }
}
