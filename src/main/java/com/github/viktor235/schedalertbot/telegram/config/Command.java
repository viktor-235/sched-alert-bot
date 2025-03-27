package com.github.viktor235.schedalertbot.telegram.config;

import com.github.viktor235.schedalertbot.telegram.TelegramUser;
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
    private final Consumer<Context> action;

    public void execute(Context ctx) {
        action.accept(ctx);
    }

    public record Context(
            String userId,
            String username,
            String msg,
            TelegramUser user) {
    }
}
