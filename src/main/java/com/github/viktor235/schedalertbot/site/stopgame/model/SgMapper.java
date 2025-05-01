package com.github.viktor235.schedalertbot.site.stopgame.model;

import org.mapstruct.*;

import java.time.Instant;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SgMapper {

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "endedAt", ignore = true)
    @BeanMapping(ignoreUnmappedSourceProperties = {"nowLive"})
    @Mapping(target = "date", source = "webEvent.date", qualifiedByName = "handleNullDate")
    void updateFromWeb(SgEventWeb webEvent, @MappingTarget SgEventEntry dbEvent, @Context Instant dbDate);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "endedAt", ignore = true)
    @BeanMapping(ignoreUnmappedSourceProperties = {"nowLive"})
    SgEventEntry toEntry(SgEventWeb web);

    @Mapping(target = "nowLive", source = "db", qualifiedByName = "isNowLive")
    @BeanMapping(ignoreUnmappedSourceProperties = {"status", "createdAt", "updatedAt", "version", "startedAt", "endedAt"})
    SgEventWeb toWeb(SgEventEntry db);

    @Named("handleNullDate")
    default Instant handleNullDate(Instant webDate, @Context Instant dbDate) {
        return webDate != null ? webDate : dbDate;
    }

    @Named("isNowLive")
    default boolean isNowLive(SgEventEntry db) {
        return db.getStatus() == EventStatus.LIVE;
    }
}
