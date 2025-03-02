package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.site.stopgame.model.SgEvent;
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
class StopgameTemplateServiceTest {

    @Autowired
    private TemplateService templateService;

    @Test
    void buildMsg_whenChangedEventWithAllChanged_thenBuildUpdateMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", false);
        ctx.put("fields", Map.of(
                SgEvent.Fields.name, new TemplateField(SgEvent.Fields.name, true, "Old name", "New name"),
                SgEvent.Fields.nowLive, new TemplateField(SgEvent.Fields.nowLive, true, false, false),
                SgEvent.Fields.description, new TemplateField(SgEvent.Fields.description, true, "Old description", "New description"),
                SgEvent.Fields.date, new TemplateField(SgEvent.Fields.date, true, Instant.ofEpochSecond(0), Instant.ofEpochSecond(0).plus(1, ChronoUnit.DAYS)),
                SgEvent.Fields.participants, new TemplateField(SgEvent.Fields.participants, true, List.of("Streamer 1"), List.of("Streamer 1", "Streamer 2"))
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆙 Обновление события
                🎦 Old name → New name
                📅 01 января, 03:00 → 02 января, 03:00 (MSK)
                🧑‍🧒‍🧒 Streamer 1 → Streamer 1, Streamer 2
                ℹ️ ... → New description
                """);
    }

    @Test
    void buildMsg_whenNewEventWithoutOptionalFields_thenBuildSmallMsg() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", true);
        ctx.put("fields", Map.of(
                SgEvent.Fields.name, new TemplateField(SgEvent.Fields.name, true, null, "Name"),
                SgEvent.Fields.nowLive, new TemplateField(SgEvent.Fields.nowLive, true, null, false),
                SgEvent.Fields.description, new TemplateField(SgEvent.Fields.description, true, null, null),
                SgEvent.Fields.date, new TemplateField(SgEvent.Fields.date, true, null, Instant.ofEpochSecond(0)),
                SgEvent.Fields.participants, new TemplateField(SgEvent.Fields.participants, true, List.of(), List.of())
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
                SgEvent.Fields.name, new TemplateField(SgEvent.Fields.name, true, "Old name", ""),
                SgEvent.Fields.nowLive, new TemplateField(SgEvent.Fields.nowLive, true, false, false),
                SgEvent.Fields.description, new TemplateField(SgEvent.Fields.description, true, "Old description", null),
                SgEvent.Fields.date, new TemplateField(SgEvent.Fields.date, true, Instant.ofEpochSecond(0), null),
                SgEvent.Fields.participants, new TemplateField(SgEvent.Fields.participants, true, List.of("Streamer 1"), List.of())
        ));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                🆙 Обновление события
                🎦 Old name → <пусто>
                📅 01 января, 03:00 → <пусто>
                🧑‍🧒‍🧒 Streamer 1 → <пусто>
                ℹ️ ... → <пусто>
                """);
    }

    @Test
    void buildMsg_whenEventWithoutParticipants_thenBuildMsgWithoutParticipants() {
        String templateName = SgProcessor.TEMPLATE_NAME;
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("newEvent", true);
        ctx.put("fields", Map.of(
                SgEvent.Fields.name, new TemplateField(SgEvent.Fields.name, true, null, "Name"),
                SgEvent.Fields.nowLive, new TemplateField(SgEvent.Fields.nowLive, true, false, false),
                SgEvent.Fields.description, new TemplateField(SgEvent.Fields.description, true, "", "Description"),
                SgEvent.Fields.date, new TemplateField(SgEvent.Fields.date, true, null, Instant.ofEpochSecond(0)),
                SgEvent.Fields.participants, new TemplateField(SgEvent.Fields.participants, true, emptyList(), emptyList())
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
                SgEvent.Fields.name, new TemplateField(SgEvent.Fields.name, false, "Name", "Name"),
                SgEvent.Fields.nowLive, new TemplateField(SgEvent.Fields.nowLive, true, false, true),
                SgEvent.Fields.description, new TemplateField(SgEvent.Fields.description, false, "Description", "Description"),
                SgEvent.Fields.date, new TemplateField(SgEvent.Fields.date, true, Instant.ofEpochSecond(0), null),
                SgEvent.Fields.participants, new TemplateField(SgEvent.Fields.participants, false, emptyList(), emptyList())
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
