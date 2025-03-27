package com.github.viktor235.schedalertbot.telegram.config;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public class BotConfig {

    private String parseMode;
    @Singular
    private List<Command> commands;
    @Singular
    private List<Callback> callbacks;

    public Command getCommand(String message) {
        return commands.stream()
                .filter(cmd -> message.toLowerCase().startsWith(cmd.getName().toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    public Callback getCallback(String callbackData) {
        return callbacks.stream()
                .filter(cb -> cb.getName().equals(callbackData))
                .findFirst()
                .orElse(null);
    }
}
