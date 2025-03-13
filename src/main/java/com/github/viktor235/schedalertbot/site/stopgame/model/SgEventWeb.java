package com.github.viktor235.schedalertbot.site.stopgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@FieldNameConstants
@NoArgsConstructor
@AllArgsConstructor
public class SgEventWeb {

    private String id;
    private String name;
    private Instant date;
    private String description;
    private List<String> participants;
    private boolean nowLive;
    private String imageUrl;
}
