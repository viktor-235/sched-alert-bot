package com.github.viktor235.schedalertbot;

import com.github.viktor235.schedalertbot.telegram.TelegramService;
import io.mongock.runner.springboot.EnableMongock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@EnableMongock
@SpringBootApplication(exclude = ThymeleafAutoConfiguration.class)
@EnableMongoRepositories({
        "com.github.viktor235.schedalertbot.telegram",
        "com.github.viktor235.schedalertbot.site.*.model"})
@EnableScheduling
@Slf4j
public class SchedAlertBotApplication {

    public static void main(String[] args) throws TelegramApiException {
        ConfigurableApplicationContext ctx = SpringApplication.run(SchedAlertBotApplication.class, args);
        TelegramService telegramService = ctx.getBean(TelegramService.class);
        if (!telegramService.isEnabled()) {
            log.warn("Telegram bot token is not set: the bot is disabled and no messages are sent. Events are still processed");
            return;
        }
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(telegramService);
    }
}
