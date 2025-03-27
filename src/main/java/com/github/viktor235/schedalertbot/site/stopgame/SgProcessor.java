package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.compare.CompareService;
import com.github.viktor235.schedalertbot.compare.FieldDiff;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventEntry;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventRepository;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventWeb;
import com.github.viktor235.schedalertbot.site.stopgame.model.SgMapper;
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
    private final SgMapper mapper;

    public void process() {
        List<TelegramUser> users = tgService.getUsers();//todo filter users who subscribed to this site
        if (users.isEmpty()) {
            log.info("No users subscribed to Stopgame. Skipping processing");
            return;
        }

        List<SgEventWeb> events = pageParser.parse();
        log.info("Found {} streams", events.size());
        events.stream()
                .map(this::getDbVersion)
                .map(this::compare)
                .filter(EventSnapshot::changed) //todo check isInFuture
                .map(this::generateMsg)
                .map(this::sendTgMsg)
                .forEach(this::saveChanges);
    }

    private EventSnapshot getDbVersion(SgEventWeb webEvent) {
        log.debug("Retrieving db data for {}", webEvent);
        SgEventEntry dbEvent = repo.findById(webEvent.getId())
                .orElse(null);
        return EventSnapshot.init(dbEvent, webEvent);
    }

    private EventSnapshot compare(EventSnapshot event) {
        SgEventWeb db = mapper.toWeb(event.db);
        SgEventWeb web = event.web;
        log.debug("Comparing db and web records:\n{}\n{}", db, web);
        List<FieldDiff> fieldDiffs = compareService.compare(db, web);
        EventSnapshot newEvent = event.withDiffReport(!fieldDiffs.isEmpty(), fieldDiffs);
        log.info("Comparison result (changed={}): {}", newEvent.changed, newEvent);
        return newEvent;
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
                SgEventWeb.Fields.name, genTemplField(SgEventWeb.Fields.name, changesMap, event.web.getName()),
                SgEventWeb.Fields.nowLive, genTemplField(SgEventWeb.Fields.nowLive, changesMap, event.web.isNowLive()),
                SgEventWeb.Fields.date, genTemplField(SgEventWeb.Fields.date, changesMap, event.web.getDate()),
                SgEventWeb.Fields.participants, genTemplField(SgEventWeb.Fields.participants, changesMap, event.web.getParticipants()),
                SgEventWeb.Fields.description, genTemplField(SgEventWeb.Fields.description, changesMap, event.web.getDescription()),
                SgEventWeb.Fields.imageUrl, genTemplField(SgEventWeb.Fields.imageUrl, changesMap, event.web.getImageUrl())
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
        tgService.getUsers().forEach(usr ->
                tgService.sendPhotoMessage(usr.getTargetChatId(), event.web.getImageUrl(), event.message)
        );
        return event;
    }

    private EventSnapshot saveChanges(EventSnapshot event) {
        if (event.db != null) {
            mapper.updateFromWeb(event.web, event.db);
            repo.save(event.db);
        } else {
            repo.save(
                    mapper.toEntry(event.web)
            );
        }
        return event;
    }

    public record EventSnapshot(SgEventEntry db,
                                SgEventWeb web,
                                boolean changed,
                                List<FieldDiff> fieldDiffs,
                                String message) {

        public static EventSnapshot init(SgEventEntry db, SgEventWeb web) {
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
