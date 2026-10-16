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
import java.util.LinkedHashMap;
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
                .map(this::normalize)
                .map(this::compare)
                .map(this::updateStatus)
                .map(this::decide)
                .forEach(this::handle);
    }

    private void handle(EventSnapshot event) {
        if (!event.shouldSave()) {
            return;
        }
        // Save before sending: a failed send loses one message, a failed save would repeat it on every run
        saveChanges(event);
        if (event.notification() != NotificationType.NONE) {
            sendTgMsg(event, generateMsg(event));
        }
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
                .collect(Collectors.toMap(SgEventWeb::getId, Function.identity(), (first, duplicate) -> {
                    log.warn("Duplicate event {} on the page, keeping the first occurrence. Ignored: {}", first.getId(), duplicate);
                    return first;
                }, LinkedHashMap::new));
        Map<String, SgEventEntry> dbEvents = repo.findAllByStatusIn(Set.of(EventStatus.SCHEDULED, EventStatus.LIVE)).stream()
                .collect(Collectors.toMap(SgEventEntry::getId, Function.identity(), (first, duplicate) -> first, LinkedHashMap::new));

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

    /**
     * The site shows no date while an event is live. Keeps the stored date in that case,
     * so that the missing date is neither a field change nor saved.
     */
    EventSnapshot normalize(EventSnapshot event) {
        if (event.web != null && event.web.getDate() == null && event.db != null && event.db.getDate() != null) {
            return event.withWeb(event.web.toBuilder()
                    .date(event.db.getDate())
                    .build());
        }
        return event;
    }

    EventSnapshot compare(EventSnapshot event) {
        SgEventWeb db = mapper.toWeb(event.db);
        SgEventWeb web = event.web;
        log.debug("Comparing db and web records:\n{}\n{}", db, web);
        List<FieldDiff> fieldDiffs = compareService.compare(db, web);
        EventSnapshot newEvent = event.withDiffReport(fieldDiffs);
        log.debug("Comparison result: {}", newEvent);
        return newEvent;
    }

    EventSnapshot updateStatus(EventSnapshot event) {
        EventStatus prevStatus = event.db != null ? event.db.getStatus() : null;
        EventStatus newStatus;
        if (event.web == null) { // Event disappeared from the site
            switch (prevStatus) {
                case EventStatus.LIVE -> newStatus = EventStatus.FINISHED; // Event has ended
                case EventStatus.SCHEDULED -> newStatus = isNotStartedYet(event.db)
                        ? EventStatus.CANCELED // Event was canceled before start
                        : EventStatus.FINISHED; // Event most likely aired without being seen live
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
            if (prevStatus == EventStatus.CANCELED || prevStatus == EventStatus.FINISHED) {
                log.info("{} event {} has returned to the site", prevStatus, event.db.getId());
            }
            newStatus = event.web.isNowLive() ? EventStatus.LIVE : EventStatus.SCHEDULED;
        }
        log.debug("Updating event status: {} -> {}", prevStatus, newStatus);
        return event.withNewStatus(newStatus);
    }

    private boolean isNotStartedYet(SgEventEntry db) {
        return db.getDate() != null && !db.getDate().isBefore(Instant.now(clock));
    }

    EventSnapshot decide(EventSnapshot event) {
        EventStatus prevStatus = event.db != null ? event.db.getStatus() : null;
        // Diffs against a missing side only describe the appearing or disappearing event, not a field change
        boolean hasFieldDiffs = event.db != null && event.web != null && !event.fieldDiffs.isEmpty();
        boolean shouldSave = event.db == null || event.newStatus != prevStatus || hasFieldDiffs;
        NotificationType notification = shouldSave
                ? NotificationType.of(prevStatus, event.newStatus, hasFieldDiffs)
                : NotificationType.NONE;
        log.info("Event {} '{}': {} -> {}, changed={}, save={}, notification={}",
                event.db != null ? event.db.getId() : event.web.getId(),
                event.web != null ? event.web.getName() : event.db.getName(),
                prevStatus, event.newStatus,
                hasFieldDiffs ? event.fieldDiffs.stream().map(FieldDiff::getName).toList() : List.of(),
                shouldSave, notification);
        return event.withDecision(shouldSave, notification);
    }

    private String generateMsg(EventSnapshot event) {
        log.debug("Generating post text for {}", event);
        Map<String, Object> ctx = new HashMap<>();

        Map<String, FieldDiff> changesMap = event.fieldDiffs.stream()
                .collect(Collectors.toMap(
                        FieldDiff::getName,
                        Function.identity()
                ));

        ctx.put("newEvent", event.db == null);
        ctx.put("notification", event.notification.name());
        ctx.put("fields", Map.of(
                SgEventWeb.Fields.name, genTemplField(SgEventWeb.Fields.name, changesMap, event.web != null ? event.web.getName() : null),
                SgEventWeb.Fields.date, genTemplField(SgEventWeb.Fields.date, changesMap, event.web != null ? event.web.getDate() : null),
                SgEventWeb.Fields.participants, genTemplField(SgEventWeb.Fields.participants, changesMap, event.web != null ? event.web.getParticipants() : null),
                SgEventWeb.Fields.description, genTemplField(SgEventWeb.Fields.description, changesMap, event.web != null ? event.web.getDescription() : null),
                SgEventWeb.Fields.imageUrl, genTemplField(SgEventWeb.Fields.imageUrl, changesMap, event.web != null ? event.web.getImageUrl() : null)
        ));

        return msgBuildingService.buildMsg(TEMPLATE_NAME, ctx);
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

    private void sendTgMsg(EventSnapshot event, String message) {
        String img = ObjectUtils.firstNonNull(
                event.web != null ? event.web.getImageUrl() : null,
                event.db != null ? event.db.getImageUrl() : null);
        for (TelegramUser usr : tgService.getUsers()) {
            tgService.sendPhotoMessage(usr.getTargetChatId(), img, message);
        }
    }

    void saveChanges(EventSnapshot event) {
        SgEventEntry result = event.db != null
                ? event.db
                : mapper.toEntry(event.web);
        if (event.web != null && event.db != null) {
            mapper.updateFromWeb(event.web, result);
        }

        EventStatus prevStatus = event.db != null ? event.db.getStatus() : null;
        Instant now = Instant.now(clock);
        if (prevStatus != EventStatus.LIVE && event.newStatus == EventStatus.LIVE) {
            result.setStartedAt(now);
            result.setEndedAt(null);
        } else if (prevStatus == EventStatus.LIVE && event.newStatus == EventStatus.FINISHED) {
            result.setEndedAt(now);
        }

        result.setStatus(event.newStatus);
        repo.save(result);
    }

    public record EventSnapshot(SgEventEntry db,
                                SgEventWeb web,
                                EventStatus newStatus,
                                List<FieldDiff> fieldDiffs,
                                boolean shouldSave,
                                NotificationType notification) {

        public static EventSnapshot init(SgEventEntry db, SgEventWeb web) {
            return new EventSnapshot(db, web, null, List.of(), false, NotificationType.NONE);
        }

        public EventSnapshot withWeb(SgEventWeb web) {
            return new EventSnapshot(db, web, newStatus, fieldDiffs, shouldSave, notification);
        }

        public EventSnapshot withDiffReport(List<FieldDiff> fieldDiffs) {
            return new EventSnapshot(db, web, newStatus, fieldDiffs, shouldSave, notification);
        }

        public EventSnapshot withNewStatus(EventStatus status) {
            return new EventSnapshot(db, web, status, fieldDiffs, shouldSave, notification);
        }

        public EventSnapshot withDecision(boolean shouldSave, NotificationType notification) {
            return new EventSnapshot(db, web, newStatus, fieldDiffs, shouldSave, notification);
        }
    }
}
