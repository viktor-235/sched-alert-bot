package com.github.viktor235.schedalertbot.telegram;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

import static com.github.viktor235.schedalertbot.telegram.TelegramUser.COLLECTION_NAME;

@Document(COLLECTION_NAME)
@Getter
@Setter
@Builder
@ToString
public class TelegramUser {

    public static final String COLLECTION_NAME = "users";

    @Id
    private String id;
    @Field
    private String username;
    @Field
    private String targetChatId;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    @Version
    private Long version;
}
