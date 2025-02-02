package com.github.viktor235.schedalertbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final TelegramUserRepository userRepository;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long userId = message.getFrom().getId();
            String username = message.getFrom().getUserName();
            String text = message.getText();

            switch (text.toLowerCase()) {
                case "/start": {
                    registerUser(userId, username);
                    break;
                }
                case "/config": {
                    sendMessage(userId, "Not implemented yet");
                    //todo sites, tz
                    break;
                }
                default: {
                    sendMessage(userId, "Unknown command. Try:\n- `/start` to register user or\n- `/config` to set the bot up'");
                }
            }
        }
    }

    private void registerUser(long userId, String username) {
        TelegramUser usr = TelegramUser.builder()
                .id(userId)
                .username(username)
                .isAdmin(false)
                .build();
        userRepository.save(usr);
        log.info("New user registered: {}", usr);
        sendMessage(userId, "New user registered");
    }

    public void sendMessage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setParseMode("Markdown");
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    public List<TelegramUser> getUsers() {
        return userRepository.findAll();
    }
}