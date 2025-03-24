package com.github.viktor235.schedalertbot.telegram.config;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public class BotConfig {

    @Singular
    List<Command> commands;

    public Command getCommand(String message) {
        return commands.stream()
                .filter(cmd -> message.toLowerCase().startsWith(cmd.getName().toLowerCase()))
                .findFirst()
                .orElse(null);
    }
}
