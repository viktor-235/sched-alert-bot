package com.github.viktor235.schedalertbot.telegram;

public interface CommandHandler {

    String getCommand();

    String getDescription();

    boolean isAuthRequired();

    boolean isAdminOnly();

    void execute(AbstractTelegramService.CommandContext context);
}
