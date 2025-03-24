package com.github.viktor235.schedalertbot.telegram.config;

import com.github.viktor235.schedalertbot.telegram.TelegramUser;

public record CommandContext(String userId, String username, String msg, TelegramUser user) {
}
