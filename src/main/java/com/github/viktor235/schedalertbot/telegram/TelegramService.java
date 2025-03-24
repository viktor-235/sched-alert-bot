package com.github.viktor235.schedalertbot.telegram;

import com.github.viktor235.schedalertbot.telegram.config.BotConfig;
import com.github.viktor235.schedalertbot.telegram.config.Command;
import com.github.viktor235.schedalertbot.telegram.config.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class TelegramService extends AbstractTelegramService {

    public TelegramService(TelegramUserRepository userRepository) {
        super(userRepository);
    }

    @Override
    protected BotConfig initBot() {
        return BotConfig.builder()
                .command(Command.builder()
                        .name("/start")
                        .description("Register new user or reset settings")
                        .authRequired(false)
                        .handler(this::handleStart)
                        .build())
                .command(Command.builder()
                        .name("/channel")
                        .description("Set up channel")
                        .handler(this::handleChannel)
                        .build())
                .command(Command.builder()
                        .name("/status")
                        .description("Show current status")
                        .adminOnly(true)
                        .handler(this::handleStatus)
                        .build())
                .command(Command.builder()
                        .name("/stop")
                        .description("Unsubscribe from bot")
                        .handler(this::handleStop)
                        .build())
                .build();
    }

    private void handleStart(CommandContext ctx) {
        TelegramUser usr = TelegramUser.builder()
                .id(ctx.userId())
                .username(ctx.username())
                .channelId(null)
                .build();
        userRepository.save(usr);
        log.info("New user registered: {}", usr);
        sendMessage(ctx.userId(), "New user registered");
    }

    private void handleChannel(CommandContext ctx) {
        if (ctx.msg().matches("/channel @\\w+")) {
            String channelId = ctx.msg().substring(ctx.msg().indexOf("@"));
            ctx.user().setChannelId(channelId);
            userRepository.save(ctx.user());
            sendMessage(ctx.userId(), "Channel %s successfully registered".formatted(channelId));
            log.info("Channel {} registered for user {}", channelId, ctx.user());
            //TODO send test message to the channel
        } else {
            String currentChannel = ctx.user().getChannelId() != null ? ctx.user().getChannelId() : "not set";
            sendMessage(ctx.userId(), """
                    Current channel: `%s`
                    To set a new channel, send command in format:
                    /channel @<ChannelName>""".formatted(currentChannel));
        }
    }

    private void handleStatus(CommandContext commandContext) {
        String serverTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        sendMessage(commandContext.userId(), """
                Server time:
                %s
                
                User:
                %s
                
                Channel:
                %s""".formatted(serverTime, commandContext.user(), commandContext.user().getChannelId()));
    }

    private void handleStop(CommandContext ctx) {
        if (userRepository.existsById(ctx.userId())) {
            userRepository.deleteById(ctx.userId());
            log.info("User unregistered: {}", ctx.userId());
            sendMessage(ctx.userId(), "You have been successfully unsubscribed from the bot. Use /start command to subscribe again.");
        } else {
            sendMessage(ctx.userId(), "You are not registered. Use /start command to register.");
        }
    }
}
