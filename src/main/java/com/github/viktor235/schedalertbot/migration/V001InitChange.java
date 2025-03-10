package com.github.viktor235.schedalertbot.migration;

import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "initializer", order = "001", author = "victor")
@RequiredArgsConstructor
public class V001InitChange {

    private final MongoTemplate mongoTemplate;

    @Execution
    public void changeSet() {
        MigrationUtils.createCollectionIfNotExists(mongoTemplate, SgEventEntry.COLLECTION_NAME);
    }

    @RollbackExecution
    public void rollbackExecution() {
    }
}