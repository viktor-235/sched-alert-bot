package com.github.viktor235.schedalertbot.telegram;

import com.github.viktor235.schedalertbot.telegram.config.BotConfig;
import com.github.viktor235.schedalertbot.telegram.config.Callback;
import com.github.viktor235.schedalertbot.telegram.config.Command;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class TelegramService extends AbstractTelegramService {

    private static final String SEND_TEST_MESSAGE = "send_test_message";

    public TelegramService(TelegramUserRepository userRepository) {
        super(userRepository);
    }

    @Override
    protected BotConfig initBot() {
        return BotConfig.builder()
                .parseMode(ParseMode.HTML)
                .command(Command.builder()
                        .name("/start")
                        .description("Register new user or reset settings")
                        .authRequired(false)
                        .action(this::handleStart)
                        .build())
                .command(Command.builder()
                        .name("/target")
                        .description("Set up target chat. Events will be sent to this chat")
                        .action(this::handleTarget)
                        .build())
                .command(Command.builder()
                        .name("/status")
                        .description("Show current status")
                        .adminOnly(true)
                        .action(this::handleStatus)
                        .build())
                .command(Command.builder()
                        .name("/stop")
                        .description("Unsubscribe from bot")
                        .action(this::handleStop)
                        .build())
                .callback(Callback.builder()
                        .name(SEND_TEST_MESSAGE)
                        .action(this::handleSendTestMessage)
                        .build())
                .build();
    }

    private void handleStart(Command.Context ctx) {
        TelegramUser usr = TelegramUser.builder()
                .id(ctx.userId())
                .username(ctx.username())
                .targetChatId(ctx.userId())
                .build();
        userRepository.save(usr);
        log.info("New user registered: {}", usr);
        sendMessage(ctx.userId(), "New user registered");
    }

    private void handleTarget(Command.Context ctx) {
        if ("/target".equalsIgnoreCase(ctx.msg())) { // If command without args
            String currentChannel = ctx.user().getTargetChatId();
            if (Objects.equals(ctx.user().getId(), ctx.user().getTargetChatId())) {
                currentChannel += " (this chat)";
            }
            sendMessage(
                    ctx.userId(), """
                            Current target chat: %s
                            To set a new target, send command <code>/target chatId</code>
                            Examples:
                            <code>/target @channelId</code>
                            <code>/target userId</code>
                            For this chat:
                            <code>/target %s</code>""".formatted(currentChannel, ctx.userId()),
                    replyTestMsg()
            );
            return;
        }
        // If command with args
        String channelId = ctx.msg().substring(ctx.msg().indexOf(" ") + 1).trim();
        if (StringUtils.isEmpty(channelId)) {
            sendMessage(ctx.userId(), "Target chat id can't be empty");
            return;
        }
        ctx.user().setTargetChatId(channelId);
        userRepository.save(ctx.user());
        sendMessage(
                ctx.userId(),
                "New target chat is %s".formatted(channelId),
                replyTestMsg()
        );
        log.info("New target chat '{}' registered for user {}", channelId, ctx.user());
    }

    private void handleStatus(Command.Context ctx) {
        String serverTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        sendMessage(ctx.userId(), """
                Server time:
                %s
                
                User:
                %s
                
                Target chat:
                %s""".formatted(serverTime, ctx.user(), ctx.user().getTargetChatId()));
    }

    private void handleStop(Command.Context ctx) {
        if (userRepository.existsById(ctx.userId())) {
            userRepository.deleteById(ctx.userId());
            log.info("User unregistered: {}", ctx.userId());
            sendMessage(ctx.userId(), "You have been successfully unsubscribed from the bot. Use /start command to subscribe again.");
        } else {
            sendMessage(ctx.userId(), "You are not registered. Use /start command to register.");
        }
    }

    private InlineKeyboardMarkup replyTestMsg() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("Send test message");
        button.setCallbackData(SEND_TEST_MESSAGE);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(
                List.of(
                        List.of(button)
                )
        );
        return markup;
    }

    private void handleSendTestMessage(Long chatId) {
        userRepository.findById(String.valueOf(chatId))
                .ifPresent(user -> sendMessage(user.getTargetChatId(), "Test message"));
    }
}
