package com.github.viktor235.schedalertbot.site.stopgame;

import com.github.viktor235.schedalertbot.compare.CompareService;
import com.github.viktor235.schedalertbot.compare.FieldDiff;
import com.github.viktor235.schedalertbot.site.stopgame.model.*;
import com.github.viktor235.schedalertbot.telegram.TelegramService;
import com.github.viktor235.schedalertbot.telegram.TelegramUser;
import com.github.viktor235.schedalertbot.template.TemplateField;
import com.github.viktor235.schedalertbot.template.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Clock clock;

    public void process() {
        List<TelegramUser> users = tgService.getUsers();//todo filter users who subscribed to this site
        if (users.isEmpty()) {
            log.info("No users subscribed to Stopgame. Skipping processing");
            return;
        }

        getData().stream()
                .map(this::compare)
                .map(this::updateStatus)
                .filter(EventSnapshot::changed)
                .map(this::generateMsg)
                .map(this::sendTgMsg)
                .forEach(this::saveChanges);
    }

    /**
     * Retrieves and merges event data from web source and database.
     *
     * @return list of {@link EventSnapshot} containing merged event data.
     * <br/>Each {@link EventSnapshot} may contain:
     * <ul>
     *   <li>only web event (new event)</li>
     *   <li>only database event (canceled event)</li>
     *   <li>both events (updated event)</li>
     * </ul>
     */
    private List<EventSnapshot> getData() {
        // Create maps for web and database events
        Map<String, SgEventWeb> webEvents = pageParser.parse().stream()
                .collect(Collectors.toMap(SgEventWeb::getId, Function.identity()));
        Map<String, SgEventEntry> dbEvents = repo.findAllByStatusIn(Set.of(EventStatus.SCHEDULED, EventStatus.LIVE)).stream()
                .collect(Collectors.toMap(SgEventEntry::getId, Function.identity()));

        // To find events which presented on site but canceled or finished in DB
        Set<String> onSiteButCanceledIds = webEvents.keySet().stream()
                .filter(e -> !dbEvents.containsKey(e))
                .collect(Collectors.toSet());
        Map<String, SgEventEntry> onSiteButCanceled = repo.findAllByIdIn(onSiteButCanceledIds).stream()
                .collect(Collectors.toMap(SgEventEntry::getId, Function.identity()));
        dbEvents.putAll(onSiteButCanceled);

        log.info("Found {} web events and {} db events", webEvents.size(), dbEvents.size());

        Set<String> allKeys = SetUtils.union(webEvents.keySet(), dbEvents.keySet());

        // Perform full outer join
        return allKeys.stream()
                .map(id -> {
                    SgEventWeb webEvent = webEvents.get(id); // may be null
                    SgEventEntry dbEvent = dbEvents.get(id); // may be null
                    return EventSnapshot.init(dbEvent, webEvent);
                })
                .toList();
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

    EventSnapshot updateStatus(EventSnapshot event) {
        EventStatus prevStatus = event.db != null ? event.db.getStatus() : null;
        EventStatus newStatus;
        if (event.web == null) { // Event disappeared from the site
            switch (prevStatus) {
                case EventStatus.LIVE -> newStatus = EventStatus.FINISHED; // Event has ended
                case EventStatus.SCHEDULED -> newStatus = EventStatus.CANCELED;  // Event was canceled before start
                case null -> {
                    newStatus = EventStatus.CANCELED;
                    log.warn("Event {} has no status in DB", event.db != null ? event.db.getId() : null);
                }
                default -> {
                    newStatus = prevStatus;
                    log.warn("Unexpected status for event {}: {}", event.db.getId(), prevStatus);
                }
            }
        } else { // Event exists on the site
            if (prevStatus == EventStatus.CANCELED) { // When a canceled event returns to the site
                log.info("Canceled event {} has returned to the site", event.db.getId());
                newStatus = event.web.isNowLive() ? EventStatus.LIVE : EventStatus.SCHEDULED;
            } else {
                if (event.web.isNowLive()) {
                    newStatus = EventStatus.LIVE;
                } else {
                    newStatus = EventStatus.SCHEDULED;
                }
            }
        }
        log.debug("Updating event status: {} -> {}", prevStatus, newStatus);
        return event.withNewStatus(newStatus);
    }

    private EventSnapshot generateMsg(EventSnapshot event) {
        log.debug("Generating post text for {}", event);
        Map<String, Object> ctx = new HashMap<>();

        Map<String, FieldDiff> changesMap = event.fieldDiffs.stream()
                .collect(Collectors.toMap(
                        FieldDiff::getName,
                        Function.identity()
                ));

        ctx.put("newEvent", event.db == null);
        ctx.put("fields", Map.of(
                "status", new TemplateField("status", false, event.db != null ? event.db.getStatus() : null, event.newStatus),
                SgEventWeb.Fields.name, genTemplField(SgEventWeb.Fields.name, changesMap, event.web != null ? event.web.getName() : null),
                SgEventWeb.Fields.date, genTemplField(SgEventWeb.Fields.date, changesMap, event.web != null ? event.web.getDate() : null),
                SgEventWeb.Fields.participants, genTemplField(SgEventWeb.Fields.participants, changesMap, event.web != null ? event.web.getParticipants() : null),
                SgEventWeb.Fields.description, genTemplField(SgEventWeb.Fields.description, changesMap, event.web != null ? event.web.getDescription() : null),
                SgEventWeb.Fields.imageUrl, genTemplField(SgEventWeb.Fields.imageUrl, changesMap, event.web != null ? event.web.getImageUrl() : null)
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
        String img = ObjectUtils.firstNonNull(
                event.web != null ? event.web.getImageUrl() : null,
                event.db != null ? event.db.getImageUrl() : null);
        for (TelegramUser usr : tgService.getUsers()) {
            tgService.sendPhotoMessage(usr.getTargetChatId(), img, event.message);
        }
        return event;
    }

    private void saveChanges(EventSnapshot event) {
        SgEventEntry result = event.db != null
                ? event.db
                : mapper.toEntry(event.web);
        if (event.web != null && event.db != null) {
            mapper.updateFromWeb(event.web, result, event.db.getDate());
        }

        switch (event.newStatus) {
            case SCHEDULED -> {
                // Nothing to do here
            }
            case LIVE -> result.setStartedAt(Instant.now(clock));
            case FINISHED, CANCELED -> result.setEndedAt(Instant.now(clock));
        }

        result.setStatus(event.newStatus);
        repo.save(result);
    }

    public record EventSnapshot(SgEventEntry db,
                                SgEventWeb web,
                                EventStatus newStatus,
                                boolean changed,
                                List<FieldDiff> fieldDiffs,
                                String message) {

        public static EventSnapshot init(SgEventEntry db, SgEventWeb web) {
            return new EventSnapshot(db, web, null, true, null, null);
        }

        public EventSnapshot withDiffReport(boolean changed, List<FieldDiff> fieldDiffs) {
            return new EventSnapshot(db, web, newStatus, changed, fieldDiffs, message);
        }

        public EventSnapshot withMessage(String msg) {
            return new EventSnapshot(db, web, newStatus, changed, fieldDiffs, msg);
        }

        public EventSnapshot withNewStatus(EventStatus status) {
            return new EventSnapshot(db, web, status, changed, fieldDiffs, message);
        }
    }
}
