package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventWeb;
import com.github.viktor235.schedalertbot.template.FreeMarkerConfig;
import com.github.viktor235.schedalertbot.template.TemplateField;
import com.github.viktor235.schedalertbot.template.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(classes = {TemplateService.class, FreeMarkerConfig.class})
class SgTemplateServiceTest {

    @Autowired
    private TemplateService templateService;

    @Test
    void buildMsg_whenChangedEventWithAllChanged_thenBuildUpdateMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.UPDATE.name());
        ctx.put("fields", Map.of(
                                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, "Old name", "New name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, true, "Old description", "New description"),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, Instant.ofEpochSecond(0), Instant.ofEpochSecond(0).plus(1, ChronoUnit.DAYS)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, true, List.of("Streamer 1"), List.of("Streamer 1", "Streamer 2")),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, true, "https://example.com/1.jpg", "https://example.com/2.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆙 Обновление события
                🎦 Old name → New name
                📅 01 января, 03:00 → 02 января, 03:00 (MSK)
                🧑‍🧒‍🧒 Streamer 1 → Streamer 1, Streamer 2
                ℹ️ ... → New description
                🖼️ Новый постер
                """);
    }

    @Test
    void buildMsg_whenNewEventWithoutOptionalFields_thenBuildSmallMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", true);
        ctx.put("notification", NotificationType.NEW.name());
        ctx.put("fields", Map.of(
                                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, null, "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, true, null, null),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, null, Instant.ofEpochSecond(0)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, true, List.of(), List.of()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, true, null, null)
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆕 Новое событие
                🎦 Name
                📅 01 января, 03:00 (MSK)
                """);
    }

    @Test
    void buildMsg_whenChangedEventAndNewValuesAreEmpty_thenBuildUpdateMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.UPDATE.name());
        ctx.put("fields", Map.of(
                                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, "Old name", ""),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, true, "Old description", null),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, Instant.ofEpochSecond(0), null),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, true, List.of("Streamer 1"), List.of()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, true, "https://example.com/1.jpg", "")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆙 Обновление события
                🎦 Old name → <i>пусто</i>
                📅 01 января, 03:00 → <i>пусто</i>
                🧑‍🧒‍🧒 Streamer 1 → <i>пусто</i>
                ℹ️ ... → <i>пусто</i>
                🖼️ Новый постер
                """);
    }

    @Test
    void buildMsg_whenEventWithoutParticipants_thenBuildMsgWithoutParticipants() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", true);
        ctx.put("notification", NotificationType.NEW.name());
        ctx.put("fields", Map.of(
                                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, null, "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, true, "", "Description"),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, null, Instant.ofEpochSecond(0)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, true, emptyList(), emptyList()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, true, "", "https://example.com/2.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆕 Новое событие
                🎦 Name
                📅 01 января, 03:00 (MSK)
                ℹ️ Description
                """);
    }

    @Test
    void buildMsg_whenLiveEvent_thenBuildMsgWithoutDate() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.LIVE.name());
        ctx.put("fields", Map.of(
                                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, false, "Name", "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, false, "Description", "Description"),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, false, Instant.ofEpochSecond(0), Instant.ofEpochSecond(0)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, false, emptyList(), emptyList()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, false, "https://example.com/1.jpg", "https://example.com/1.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🔴 В эфире <a href='https://www.twitch.tv/stopgameru'>Twitch</a>/<a href='https://www.youtube.com/@StopgameRuOnline'>YouTube</a>
                🎦 Name
                ℹ️ Description
                """);
    }

    @Test
    void buildMsg_whenCanceledEvent_thenBuildCancelMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.CANCELED.name());
        ctx.put("fields", Map.of(
                                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, false, "Name", "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, false, "Description", "Description"),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, false, Instant.ofEpochSecond(0), Instant.ofEpochSecond(0)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, false, emptyList(), emptyList()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, false, "https://example.com/1.jpg", "https://example.com/1.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                ❌ Отмена события
                🎦 Name
                📅 01 января, 03:00 (MSK)
                ℹ️ Description
                """);
    }

    @Test
    void buildMsg_whenReturnedEventWithoutChanges_thenBuildReturnedMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.RETURNED.name());
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, false, null, "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, false, null, "Description"),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, false, null, Instant.ofEpochSecond(0)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, false, null, List.of("Streamer 1")),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, false, null, "https://example.com/1.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🔁 Снова в расписании
                🎦 Name
                📅 01 января, 03:00 (MSK)
                🧑‍🧒‍🧒 Streamer 1
                ℹ️ Description
                """);
    }

    @Test
    void buildMsg_whenReturnedEventWithNewDate_thenBuildReturnedMsgWithDateDiff() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.RETURNED.name());
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, false, null, "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, false, null, null),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, Instant.ofEpochSecond(0), Instant.ofEpochSecond(0).plus(1, ChronoUnit.DAYS)),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, false, null, emptyList()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, false, null, "https://example.com/1.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🔁 Снова в расписании
                🎦 Name
                📅 01 января, 03:00 → 02 января, 03:00 (MSK)
                """);
    }

    @Test
    void buildMsg_whenDescriptionRemoved_thenShowEmptyMarker() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("notification", NotificationType.UPDATE.name());
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, false, null, "Name"),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, true, "Описание стрима", ""),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, false, null, null),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, false, null, emptyList()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, false, null, null)
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result)
                .isEqualTo("""
                        🆙 Обновление события
                        🎦 Name
                        ℹ️ ... → <i>пусто</i>
                        """)
                .doesNotContain("<пусто>");
    }
}
