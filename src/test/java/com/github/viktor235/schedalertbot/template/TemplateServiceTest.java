package com.github.viktor235.schedalertbot.template;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Import(TestFreeMarkerConfig.class)
class TemplateServiceTest {

    @Autowired
    private TemplateService templateService;

    @Test
    void buildMsg_whenCorrectInput_thenBuildMsg() {
        String templateName = "test.ftl";
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("str", "txt");
        ctx.put("bool", true);
        ctx.put("date", Instant.ofEpochSecond(0));

        String result = templateService.buildMsg(templateName, ctx);

        assertThat(result).isEqualTo("""
                txt
                true
                01 января, 03:00 (MSK)
                """);
    }

    @Test
    void buildMsg_whenTemplateNotFound_thenThrowException() {
        String templateName = "unknownTemplate";
        Map<String, Object> ctx = new HashMap<>();

        assertThatThrownBy(() -> templateService.buildMsg(templateName, ctx))
                .isInstanceOf(TemplateException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void buildMsg_whenCtxIsEmpty_thenThrowException() {
        String templateName = "test.ftl";
        Map<String, Object> ctx = new HashMap<>();

        assertThatThrownBy(() -> templateService.buildMsg(templateName, ctx))
                .isInstanceOf(TemplateException.class)
                .hasMessageContaining("The following has evaluated to null or missing");
    }
}