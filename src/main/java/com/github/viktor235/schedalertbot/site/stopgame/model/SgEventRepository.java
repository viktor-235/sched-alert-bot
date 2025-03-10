package com.github.viktor235.schedalertbot.site.stopgame.model;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SgEventRepository extends MongoRepository<SgEventEntry, String> {
}
