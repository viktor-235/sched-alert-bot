package com.github.viktor235.schedalertbot.telegram.config;

import lombok.Builder;
import lombok.Getter;

import java.util.function.Consumer;

@Getter
@Builder
public class Command {

    private final String name;
    private final String description;
    @Builder.Default
    private boolean authRequired = true;
    @Builder.Default
    private boolean adminOnly = false;
    private final Consumer<CommandContext> handler;

    public void execute(CommandContext ctx) {
        handler.accept(ctx);
    }
}
