package com.github.viktor235.schedalertbot.telegram;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "users")
@Getter
@Setter
@Builder
@ToString
public class TelegramUser {
    //todo audit

    @Id
    private String id;
    @Field
    private String username;
    @Field
    private String channelId;
}