package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.compare.CompareService;
import com.github.viktor235.schedalertbot.compare.FieldDiff;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEvent;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventRepository;
import com.github.viktor235.schedalertbot.telegram.TelegramService;
import com.github.viktor235.schedalertbot.telegram.TelegramUser;
import com.github.viktor235.schedalertbot.template.TemplateField;
import com.github.viktor235.schedalertbot.template.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Processes <a href="https://stopgame.ru/live_schedule">stopgame.ru</a> schedule
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SgProcessor {

    public static final String TEMPLATE_NAME = "stopgame.md.ftl";

    private final SgScraper pageParser;
    private final SgEventRepository repo;
    private final CompareService compareService;
    private final TemplateService msgBuildingService;
    private final TelegramService tgService;

    public void process() {
        List<TelegramUser> users = tgService.getUsers();//todo filter users who subscribed to this site
        if (users.isEmpty()) {
            log.info("No users subscribed to Stopgame. Skipping processing");
            return;
        }

        // Scheduled
        List<SgEvent> events = pageParser.parse();
        log.info("Found {} streams", events.size());
        events.stream()
                .map(this::getDbVersion)
                .map(this::compare)
                .map(this::debugComparison)
                .filter(EventSnapshot::changed) //todo check isInFuture
                .map(this::generateMsg)
                .map(this::sendTgMsg)
                .forEach(this::saveChanges);
    }

    private EventSnapshot getDbVersion(SgEvent webEvent) {
        log.debug("Retrieving db data for {}", webEvent);
        SgEvent dbEvent = repo.findById(webEvent.getId())
                .orElse(null);
        return EventSnapshot.init(dbEvent, webEvent);
    }

    private EventSnapshot compare(EventSnapshot event) {
        SgEvent db = event.db;
        SgEvent web = event.web;
        log.debug("Comparing db and web records:\n{}\n{}", db, web);
        List<FieldDiff> fieldDiffs = compareService.compare(db, web);
        return event.withDiffReport(!fieldDiffs.isEmpty(), fieldDiffs);
    }

    private EventSnapshot debugComparison(EventSnapshot data) {
        log.info("Comparison result (changed={}): {}", data.changed, data);
        return data;
    }

    private EventSnapshot generateMsg(EventSnapshot event) {
        log.debug("Generating post text for {}", event.web);
        Map<String, Object> ctx = new HashMap<>();

        Map<String, FieldDiff> changesMap = event.fieldDiffs.stream()
                .collect(Collectors.toMap(
                        FieldDiff::getName,
                        Function.identity()
                ));

        ctx.put("newEvent", event.db == null);
        ctx.put("fields", Map.of(
                SgEvent.Fields.name, genTemplField(SgEvent.Fields.name, changesMap, event.web.getName()),
                SgEvent.Fields.nowLive, genTemplField(SgEvent.Fields.nowLive, changesMap, event.web.isNowLive()),
                SgEvent.Fields.date, genTemplField(SgEvent.Fields.date, changesMap, event.web.getDate()),
                SgEvent.Fields.participants, genTemplField(SgEvent.Fields.participants, changesMap, event.web.getParticipants()),
                SgEvent.Fields.description, genTemplField(SgEvent.Fields.description, changesMap, event.web.getDescription())
        ));

        return event.withMessage(
                msgBuildingService.buildMsg(TEMPLATE_NAME, ctx)
        );
    }

    private TemplateField genTemplField(String fieldName, Map<String, FieldDiff> changesMap, Object val) {
        boolean changed = changesMap.containsKey(fieldName);
        return new TemplateField(
                fieldName,
                changed,
                changed ? changesMap.get(fieldName).getOldValue() : null,
                changed ? changesMap.get(fieldName).getNewValue() : val
        );
    }


    private EventSnapshot sendTgMsg(EventSnapshot event) {
        for (TelegramUser usr : tgService.getUsers()) {//TODO reuse prev users
            tgService.sendMessage(usr.getId(), event.message);
            if (usr.getChannelId() != null) {
                tgService.sendMessage(usr.getChannelId(), event.message);
            }
        }
        return event;
    }

    private EventSnapshot saveChanges(EventSnapshot event) {
        repo.save(event.web);
        return event;
    }

    public record EventSnapshot(SgEvent db,
                                SgEvent web,
                                boolean changed,
                                List<FieldDiff> fieldDiffs,
                                String message) {

        public static EventSnapshot init(SgEvent db, SgEvent web) {
            return new EventSnapshot(db, web, true, null, null);
        }

        public EventSnapshot withDiffReport(boolean changed, List<FieldDiff> fieldDiffs) {
            return new EventSnapshot(db, web, changed, fieldDiffs, message);
        }

        public EventSnapshot withMessage(String msg) {
            return new EventSnapshot(db, web, changed, fieldDiffs, msg);
        }
    }
}
