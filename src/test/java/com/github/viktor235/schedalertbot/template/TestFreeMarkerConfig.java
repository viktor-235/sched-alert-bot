package com.github.viktor235.schedalertbot.template;

import freemarker.template.Configuration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestFreeMarkerConfig {

    @Bean
    @Primary
    public Configuration testFreemarkerConfig() {
        Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(this.getClass(), "/templates/");
        cfg.setDefaultEncoding("UTF-8");
        return cfg;
    }
}
