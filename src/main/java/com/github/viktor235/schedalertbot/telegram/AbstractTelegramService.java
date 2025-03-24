package com.github.viktor235.schedalertbot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractTelegramService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    protected String botToken;

    @Value("${telegram.bot.username}")
    protected String botUsername;

    protected final TelegramUserRepository userRepository;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    protected abstract TelegramService.Command[] getCommands();

    protected abstract CommandHandler getCommand(String message);

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        setupBotCommands();
        Message message = update.getMessage();
        String userId = String.valueOf(message.getFrom().getId());
        String username = message.getFrom().getUserName();
        String msg = message.getText();

        CommandHandler command = getCommand(msg);
        if (command == null) {
            sendMessage(userId, "Unknown command. Try:\n" + getAvailableCommands());
            return;
        }

        handleCommand(command, userId, username, msg);
    }

    protected String getAvailableCommands() {
        return Arrays.stream(getCommands())
                .map(cmd -> cmd.getCommand() + " " + cmd.getDescription())
                .collect(Collectors.joining("\n"));
    }

    protected void handleCommand(CommandHandler command, String userId, String username, String msg) {
        if (!command.isAuthRequired()) {
            CommandContext ctx = new CommandContext(userId, username, msg, null);
            command.execute(ctx);
            return;
        }

        TelegramUser user = userRepository.findById(userId)
                .orElseGet(() -> {
                    sendMessage(userId, "You are not registered. Use /start command to register");
                    return null;
                });
        if (user == null) return;

        CommandContext ctx = new CommandContext(userId, username, msg, user);
        command.execute(ctx);
    }

    protected void setupBotCommands() {
        List<BotCommand> commands = new ArrayList<>();
        for (CommandHandler cmd : getCommands()) {
            commands.add(new BotCommand(cmd.getCommand(), cmd.getDescription()));
        }

        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: {}", e.getMessage(), e);
        }
    }

    public void sendMessage(String chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }

    public void sendPhotoMessage(String chatId, String imageUrl, String caption) {
        if (isEmpty(imageUrl)) {
            sendMessage(chatId, caption);
            return;
        }

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(imageUrl));
        if (isNotEmpty(caption)) {
            sendPhoto.setCaption(caption);
        }

        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Error sending photo message: {}", e.getMessage(), e);
        }
    }

    public List<TelegramUser> getUsers() {
        return userRepository.findAll();
    }

    public record CommandContext(String userId, String username, String msg, TelegramUser user) {
    }
}
