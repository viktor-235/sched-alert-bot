package com.github.viktor235.schedalertbot.site.stopgame.model;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SgEventRepository extends MongoRepository<SgEventEntry, String> {

    List<SgEventEntry> findAllByStatusIn(Collection<EventStatus> status);

    List<SgEventEntry> findAllByIdIn(Collection<String> ids);

    /**
     * Events in the given statuses scheduled or started from {@code from} on
     */
    @Query("{ status: { $in: ?0 }, $or: [ { date: { $gte: ?1 } }, { startedAt: { $gte: ?1 } } ] }")
    List<SgEventEntry> findAllByStatusInStartingFrom(Collection<EventStatus> statuses, Instant from);
}
