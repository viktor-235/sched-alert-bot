package com.github.viktor235.htmlcleaner;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class HtmlCleaner {

    public static final String URL = "https://stopgame.ru/live_schedule";
    public static final String SELECTOR = "div[data-key]:has(div[class*=_stream_])";

    public static void main(String[] args) throws IOException {
        handleFile(
                "src/test/resources/web/stopgame/events/a-b-c.html",
                "src/test/resources/web/stopgame/events/a-b-c-cleaned.html",
                SELECTOR
        );
//        handleDirectory("in-folder/", "out-folder/", SELECTOR);
//        handleUrl(URL, null, SELECTOR);
    }

    private static void handleFile(String inFilePath, String outFilePath, String selector) throws IOException {
        Document doc = getDocumentFromFile(inFilePath);
        String result = cleanSingleDoc(doc, selector);

        if (outFilePath == null) {
            System.out.println(result);
        } else {
            writeToFile(Path.of(outFilePath), result);
        }
    }

    private static void handleDirectory(String inDirPath, String outDirPath, String selector) throws IOException {
        File inputFolder = new File(inDirPath);
        File outputFolder = new File(outDirPath);

        if (!inputFolder.isDirectory()) {
            throw new IllegalArgumentException("Input path must be a directory: " + inDirPath);
        }
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outDirPath);
        }

        File[] inputFiles = inputFolder.listFiles((dir, name) -> name.endsWith(".html"));
        if (inputFiles == null) {
            throw new IOException("Failed to list HTML files in directory: " + inDirPath);
        }

        for (File inputFile : inputFiles) {
            Document doc = getDocumentFromFile(inputFile.getPath());
            String result = cleanSingleDoc(doc, selector);
            writeToFile(new File(outputFolder, inputFile.getName()).toPath(), result);
        }
    }

    private static void handleUrl(String url, String outFilePath, String selector) throws IOException {
        Document doc = getDocumentByUrl(url);
        String result = cleanSingleDoc(doc, selector);

        if (outFilePath == null) {
            System.out.println(result);
        } else {
            writeToFile(Path.of(outFilePath), result);
        }
    }

    private static String cleanSingleDoc(Document doc, String selector) {
        Document cleanDoc = Document.createShell(doc.baseUri());
        Element cleanBody = cleanDoc.body();

        // Находим все важные элементы
        Elements importantElements = doc.select(selector);
        
        // Создаем карту для хранения путей к важным элементам
        Map<Element, String> elementPaths = new HashMap<>();
        
        // Для каждого важного элемента сохраняем его путь
        for (Element important : importantElements) {
            String path = getElementPath(important);
            elementPaths.put(important, path);
        }

        // Создаем новое дерево, сохраняя структуру
        Element currentParent = cleanBody;
        String currentPath = "";
        
        for (Element important : importantElements) {
            String path = elementPaths.get(important);
            
            // Если путь отличается от текущего, создаем новую ветку
            if (!path.equals(currentPath)) {
                currentParent = cleanBody;
                String[] pathParts = path.split("/");
                
                for (String part : pathParts) {
                    if (!part.isEmpty()) {
                        Element existingChild = currentParent.select(part).first();
                        if (existingChild == null) {
                            Element newElement = new Element(part);
                            currentParent.appendChild(newElement);
                            currentParent = newElement;
                        } else {
                            currentParent = existingChild;
                        }
                    }
                }
                currentPath = path;
            }
            
            // Добавляем важный элемент в текущего родителя
            currentParent.appendChild(important.clone());
        }

        return cleanDoc.outerHtml();
    }

    private static String getElementPath(Element element) {
        StringBuilder path = new StringBuilder();
        Element current = element;
        
        while (current != null && !"body".equals(current.tagName())) {
            path.insert(0, "/" + current.tagName());
            current = current.parent();
        }
        
        return path.toString();
    }

    private static Document getDocumentByUrl(String url) throws IOException {
        return Jsoup.connect(url).get();
    }

    private static @NotNull Document getDocumentFromFile(String path) throws IOException {
        File input = new File(path);
        return Jsoup.parse(input, StandardCharsets.UTF_8.name());
    }

    private static void writeToFile(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }
}