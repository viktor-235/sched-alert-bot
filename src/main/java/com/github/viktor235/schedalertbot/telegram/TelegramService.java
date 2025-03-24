package com.github.viktor235.schedalertbot.telegram;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.BiConsumer;

@Service
@Slf4j
public class TelegramService extends AbstractTelegramService {

    @RequiredArgsConstructor
    @Getter
    public enum Command implements CommandHandler {
        START("/start", "Register new user or reset settings",
                false, false, TelegramService::handleStart),
        CHANNEL("/channel", "Set up channel",
                true, false, TelegramService::handleChannel),
        STATUS("/status", "Show current status",
                true, true, TelegramService::handleStatus),
        STOP("/stop", "Unsubscribe from bot",
                true, false, TelegramService::handleStop);

        @Setter
        private static ApplicationContext appContext;

        private final String command;
        private final String description;
        private final boolean authRequired;
        private final boolean adminOnly;
        private final BiConsumer<TelegramService, CommandContext> handler;

        public static Command findByMessage(String msg) {
            return Arrays.stream(values())
                    .filter(cmd -> msg.toLowerCase().startsWith(cmd.command.toLowerCase()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void execute(CommandContext context) {
            TelegramService tgService = appContext.getBean(TelegramService.class);
            handler.accept(tgService, context);
        }

        @Component
        public static class EnumContextInjector implements ApplicationContextAware {
            @Override
            public void setApplicationContext(ApplicationContext appContext) {
                Command.setAppContext(appContext);
            }
        }
    }

    public TelegramService(TelegramUserRepository userRepository) {
        super(userRepository);
    }

    @Override
    protected Command[] getCommands() {
        return Command.values();
    }

    @Override
    protected Command getCommand(String message) {
        return Command.findByMessage(message);
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