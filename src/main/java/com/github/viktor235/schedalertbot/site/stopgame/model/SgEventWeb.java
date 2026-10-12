package com.github.viktor235.schedalertbot.site.stopgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.javers.core.metamodel.annotation.DiffIgnore;

import java.time.Instant;
import java.util.List;

@Data
@Builder(toBuilder = true)
@FieldNameConstants
@NoArgsConstructor
@AllArgsConstructor
public class SgEventWeb {

    private String id;
    private String name;
    private Instant date;
    private String description;
    private List<String> participants;
    @DiffIgnore
    private boolean nowLive;
    private String imageUrl;
}
