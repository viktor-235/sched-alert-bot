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
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, "Old name", "New name"),
                SgEventWeb.Fields.nowLive, new TemplateField(SgEventWeb.Fields.nowLive, true, false, false),
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
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, null, "Name"),
                SgEventWeb.Fields.nowLive, new TemplateField(SgEventWeb.Fields.nowLive, true, null, false),
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
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, "Old name", ""),
                SgEventWeb.Fields.nowLive, new TemplateField(SgEventWeb.Fields.nowLive, true, false, false),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, true, "Old description", null),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, Instant.ofEpochSecond(0), null),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, true, List.of("Streamer 1"), List.of()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, true, "https://example.com/1.jpg", "")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆙 Обновление события
                🎦 Old name → <пусто>
                📅 01 января, 03:00 → <пусто>
                🧑‍🧒‍🧒 Streamer 1 → <пусто>
                ℹ️ ... → <пусто>
                🖼️ Новый постер
                """);
    }

    @Test
    void buildMsg_whenEventWithoutParticipants_thenBuildMsgWithoutParticipants() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", true);
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, true, null, "Name"),
                SgEventWeb.Fields.nowLive, new TemplateField(SgEventWeb.Fields.nowLive, true, false, false),
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
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, new TemplateField(SgEventWeb.Fields.name, false, "Name", "Name"),
                SgEventWeb.Fields.nowLive, new TemplateField(SgEventWeb.Fields.nowLive, true, false, true),
                SgEventWeb.Fields.description, new TemplateField(SgEventWeb.Fields.description, false, "Description", "Description"),
                SgEventWeb.Fields.date, new TemplateField(SgEventWeb.Fields.date, true, Instant.ofEpochSecond(0), null),
                SgEventWeb.Fields.participants, new TemplateField(SgEventWeb.Fields.participants, false, emptyList(), emptyList()),
                SgEventWeb.Fields.imageUrl, new TemplateField(SgEventWeb.Fields.imageUrl, false, "https://example.com/1.jpg", "https://example.com/1.jpg")
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🔴 В эфире [Twitch](https://www.twitch.tv/stopgameru)/[YouTube](https://www.youtube.com/@StopgameRuOnline)
                🎦 Name
                ℹ️ Description
                """);
    }

    //TODO if nowLive==true and newEvent==true
}
