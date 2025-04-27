package com.github.viktor235.schedalertbot.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@UtilityClass
public class TestUtils {

    public <T> T readJsonFile(String jsonFilePath, ObjectMapper mapper, Class<T> clazz) throws IOException {
        return mapper.readValue(
                Files.readString(Path.of(jsonFilePath)),
                mapper.getTypeFactory().constructType(clazz)
        );
    }

    public <T> List<T> readJsonListFile(String jsonFilePath, ObjectMapper mapper, Class<T> clazz) throws IOException {
        if (jsonFilePath == null) {
            return Collections.emptyList();
        }
        return mapper.readValue(
                Files.readString(Path.of(jsonFilePath)),
                mapper.getTypeFactory().constructCollectionType(List.class, clazz)
        );
    }

    public String getFileContent(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }
}
