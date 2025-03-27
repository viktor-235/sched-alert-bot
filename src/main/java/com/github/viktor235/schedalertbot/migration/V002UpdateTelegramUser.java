package com.github.viktor235.schedalertbot.migration;

import com.github.viktor235.schedalertbot.telegram.TelegramUser;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "renameTelegramUserChannelIdField", order = "002", author = "victor")
@RequiredArgsConstructor
public class V002UpdateTelegramUser {

    public static final String OLD_FIELD_NAME = "channelId";
    public static final String NEW_FIELD_NAME = "targetChatId";

    private final MongoTemplate mongoTemplate;

    @Execution
    public void changeSet() {
        MigrationUtils.renameField(
                mongoTemplate,
                TelegramUser.COLLECTION_NAME,
                OLD_FIELD_NAME,
                NEW_FIELD_NAME
        );
    }

    @RollbackExecution
    public void rollbackExecution() {
        MigrationUtils.renameField(
                mongoTemplate,
                TelegramUser.COLLECTION_NAME,
                NEW_FIELD_NAME,
                OLD_FIELD_NAME
        );
    }
}