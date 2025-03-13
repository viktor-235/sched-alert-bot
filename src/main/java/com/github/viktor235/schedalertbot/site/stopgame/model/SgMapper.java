package com.github.viktor235.schedalertbot.site.stopgame.model;

import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SgMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateFromWeb(SgEventWeb webEvent, @MappingTarget SgEventEntry dbEvent);

    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    SgEventEntry toEntry(SgEventWeb web);

    @BeanMapping(ignoreUnmappedSourceProperties = {"createdAt", "updatedAt", "version"})
    SgEventWeb toWeb(SgEventEntry db);
}
