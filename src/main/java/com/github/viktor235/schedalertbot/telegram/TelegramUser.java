package com.github.viktor235.schedalertbot.telegram;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "users")
@Getter
@Setter
@Builder
public class TelegramUser {
    //todo audit

    @Id
    private long id;
    @Field
    private String username;
    @Field
    private boolean isAdmin;
    @Field
    private String timezone;
    @Field
    private List<String> sites;
}