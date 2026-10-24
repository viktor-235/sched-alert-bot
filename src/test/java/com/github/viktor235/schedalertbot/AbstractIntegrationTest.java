package com.github.viktor235.schedalertbot;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("telegram.bot.token", () -> "test-token");
        registry.add("telegram.bot.username", () -> "test_bot");
        // Tests run the processing themselves: no scheduled runs against the real site
        registry.add("site.stopgame.scheduler.cron", () -> "-");
        // Tests never publish to GitHub, whatever application.yml says
        registry.add("site.stopgame.ics.publish.enabled", () -> "false");
    }
}
