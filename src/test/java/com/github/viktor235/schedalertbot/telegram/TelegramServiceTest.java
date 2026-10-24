package com.github.viktor235.schedalertbot.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramServiceTest {

    @Test
    void isEnabled_whenTokenBlank_thenFalse() {
        assertThat(service("").isEnabled()).isFalse();
        assertThat(service(null).isEnabled()).isFalse();
    }

    @Test
    void isEnabled_whenTokenSet_thenTrue() {
        assertThat(service("123:abc").isEnabled()).isTrue();
    }

    @Test
    void send_whenDisabled_thenNothingSent() throws Exception {
        TelegramService service = spy(service(""));

        service.sendMessage("1", "text");
        service.sendPhotoMessage("1", "https://example.com/image.jpg", "caption");
        service.sendPhotoMessage("1", null, "caption without image");

        verify(service, never()).execute(any(SendMessage.class));
        verify(service, never()).execute(any(SendPhoto.class));
    }

    private static TelegramService service(String token) {
        TelegramService service = new TelegramService(mock(TelegramUserRepository.class), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "botToken", token);
        return service;
    }
}
