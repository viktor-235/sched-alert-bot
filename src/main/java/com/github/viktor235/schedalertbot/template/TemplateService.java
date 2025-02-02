package com.github.viktor235.schedalertbot.template;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final Configuration configuration;

    public String buildMsg(String templateName, Map<String, Object> ctx) {
        try {
            Template template = configuration.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(ctx, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new com.github.viktor235.schedalertbot.template.TemplateException(
                    "Error while template processing: " + e.getMessage(), e);
        }
    }
}
