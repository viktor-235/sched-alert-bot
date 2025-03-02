package com.github.viktor235.schedalertbot.telegram;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TelegramUserRepository extends MongoRepository<TelegramUser, String> {
}
