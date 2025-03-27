package com.github.viktor235.schedalertbot.migration;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@UtilityClass
public class MigrationUtils {

    public void createCollectionIfNotExists(MongoTemplate template, String... colNames) {
        for (String colName : colNames) {
            if (!template.collectionExists(colName)) {
                template.createCollection(colName);
            }
        }
    }

    public void renameField(MongoTemplate template, String collectionName, String oldFieldName, String newFieldName) {
        template.updateMulti(
                new Query(),
                new Update().rename(oldFieldName, newFieldName),
                collectionName
        );
    }
}
