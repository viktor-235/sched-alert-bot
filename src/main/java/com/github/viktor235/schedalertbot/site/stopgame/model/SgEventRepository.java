package com.github.viktor235.schedalertbot.site.stopgame.model;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface SgEventRepository extends MongoRepository<SgEventEntry, String> {

    List<SgEventEntry> findAllByStatusIn(Collection<EventStatus> status);

    List<SgEventEntry> findAllByIdIn(Collection<String> ids);
}
