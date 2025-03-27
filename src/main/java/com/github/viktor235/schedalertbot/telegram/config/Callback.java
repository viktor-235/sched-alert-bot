package com.github.viktor235.schedalertbot.telegram.config;

import lombok.Builder;
import lombok.Getter;

import java.util.function.Consumer;

@Getter
@Builder
public class Callback {

    private final String name;
    private final Consumer<Long> action;

    public void execute(Long chatId) {
        action.accept(chatId);
    }
}
