package com.github.viktor235.schedalertbot.site.stopgame.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Document(collection = "site_stopgame_events")
@FieldNameConstants
public class SgEvent {
    //todo audit
    public static final String COLLECTION_NAME = "site_stopgame_events";

    @Id
    private String id;
    @Field
    private String name;
    @Field
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private Instant date;
    @Field
    private String description;
    @Field
    private List<String> participants;
    @Field
    private boolean nowLive;
}
