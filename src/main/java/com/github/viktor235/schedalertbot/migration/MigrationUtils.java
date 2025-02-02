package com.github.viktor235.schedalertbot.migration;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.MongoTemplate;

@UtilityClass
public class MigrationUtils {

    public void createCollectionIfNotExists(MongoTemplate template, String... colNames) {
        for (String colName : colNames) {
            if (!template.collectionExists(colName)) {
                template.createCollection(colName);
            }
        }
    }
}
