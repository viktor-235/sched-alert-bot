package com.github.viktor235.schedalertbot.telegram;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

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

    @RequiredArgsConstructor
    @Getter
    public enum Command {
        START("/start", "Register new user or reset settings", TelegramService::handleStart),
        CHANNEL("/channel", "Set up channel", TelegramService::handleChannel),
        STOP("/stop", "Unsubscribe from bot", TelegramService::handleStop);

        private final String command;
        private final String description;
        private final BiConsumer<TelegramService, CommandContext> handler;

        public static Command findByMessage(String msg) {
            String lowerMsg = msg.toLowerCase();
            return Arrays.stream(values())
                    .filter(cmd -> lowerMsg.startsWith(cmd.command.toLowerCase()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CommandContext(String userId, String username, String msg, TelegramUser user) {
    }

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

        Command command = Command.findByMessage(msg);
        if (command == null) {
            sendMessage(userId, "Unknown command. Try:\n" +
                                Arrays.stream(Command.values())
                                        .map(cmd -> cmd.getCommand() + " " + cmd.getDescription())
                                        .collect(Collectors.joining("\n")));
            return;
        }

        if (command == Command.START) {
            command.handler.accept(this, new CommandContext(userId, username, msg, null));
            return;
        }

        TelegramUser user = userRepository.findById(userId)
                .orElseGet(() -> {
                    sendMessage(userId, "You are not registered. Use /start command to register");
                    return null;
                });
        if (user == null) return;

        command.handler.accept(this, new CommandContext(userId, username, msg, user));
    }

    private void handleStart(CommandContext ctx) {
        TelegramUser usr = TelegramUser.builder()
                .id(ctx.userId)
                .username(ctx.username)
                .channelId(null)
                .build();
        userRepository.save(usr);
        log.info("New user registered: {}", usr);
        sendMessage(ctx.userId, "New user registered");
    }

    private void handleStop(CommandContext ctx) {
        if (userRepository.existsById(ctx.userId)) {
            userRepository.deleteById(ctx.userId);
            log.info("User unregistered: {}", ctx.userId);
            sendMessage(ctx.userId, "You have been successfully unsubscribed from the bot. Use /start command to subscribe again.");
        } else {
            sendMessage(ctx.userId, "You are not registered. Use /start command to register.");
        }
    }

    private void handleChannel(CommandContext ctx) {
        if (ctx.msg.matches("/channel @\\w+")) {
            String channelId = ctx.msg.substring(ctx.msg.indexOf("@"));
            ctx.user.setChannelId(channelId);
            userRepository.save(ctx.user);
            sendMessage(ctx.userId, "Channel %s successfully registered".formatted(channelId));
            log.info("Channel {} registered for user {}", channelId, ctx.user);
            //TODO send test message to the channel
        } else {
            String currentChannel = ctx.user.getChannelId() != null ? ctx.user.getChannelId() : "not set";
            sendMessage(ctx.userId, """
                    Current channel: `%s`
                    To set a new channel, send command in format:
                    /channel @<ChannelName>""".formatted(currentChannel));
        }
    }

    private void setupBotCommands() {
        List<BotCommand> commands = new ArrayList<>();
        for (Command cmd : Command.values()) {
            commands.add(new BotCommand(cmd.command, cmd.description));
        }

        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: {}", e.getMessage(), e);
        }
    }

    public void sendMessage(String chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setParseMode("Markdown");
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
            sendPhoto.setParseMode("Markdown");
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
}