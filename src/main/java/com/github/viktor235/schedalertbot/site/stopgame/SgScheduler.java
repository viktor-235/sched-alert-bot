package com.github.viktor235.schedalertbot.site.stopgame;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SgScheduler {

    private final SgProcessor processor;

    @Scheduled(cron = "*/10 * * * * *")
    public void performProcessing() {
        log.debug("Performing scheduled processing for Stopgame");
        processor.process();
    }
}