package com.github.viktor235.schedalertbot.site.stopgame.model;

import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SgMapper {

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "endedAt", ignore = true)
    @BeanMapping(ignoreUnmappedSourceProperties = {"nowLive"})
    void updateFromWeb(SgEventWeb webEvent, @MappingTarget SgEventEntry dbEvent);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "endedAt", ignore = true)
    @BeanMapping(ignoreUnmappedSourceProperties = {"nowLive"})
    SgEventEntry toEntry(SgEventWeb web);

    @Mapping(target = "nowLive", ignore = true)
    @BeanMapping(ignoreUnmappedSourceProperties = {"status", "createdAt", "updatedAt", "version", "startedAt", "endedAt"})
    SgEventWeb toWeb(SgEventEntry db);
}
