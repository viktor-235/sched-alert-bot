package com.github.viktor235.schedalertbot.template;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

@Configuration
public class FreeMarkerConfig {

    @Bean
    public freemarker.template.Configuration freemarkerConfig() throws IOException {
        freemarker.template.Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
        cfg.setDirectoryForTemplateLoading(new File("src/main/resources/templates"));
        cfg.setDefaultEncoding("UTF-8");
        return cfg;
    }
}