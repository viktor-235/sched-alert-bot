package com.github.viktor235.schedalertbot;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@EnableMongock
@SpringBootApplication(exclude = ThymeleafAutoConfiguration.class)
@EnableMongoRepositories({
        "com.github.viktor235.schedalertbot.telegram",
        "com.github.viktor235.schedalertbot.site.*.model"})
@EnableScheduling
public class SchedAlertBotApplication {

    public static void main(String[] args) throws TelegramApiException {
        ConfigurableApplicationContext ctx = SpringApplication.run(SchedAlertBotApplication.class, args);
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(ctx.getBean("telegramService", TelegramLongPollingBot.class));
    }

}


