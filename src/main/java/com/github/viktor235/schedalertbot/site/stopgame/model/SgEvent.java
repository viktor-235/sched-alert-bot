package com.github.viktor235.schedalertbot.site.stopgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.javers.core.metamodel.annotation.DiffIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Document(collection = "site_stopgame_events")
@FieldNameConstants
@NoArgsConstructor
@AllArgsConstructor
public class SgEvent {
    //todo audit
    public static final String COLLECTION_NAME = "site_stopgame_events";

    @Id
    private String id;
    @Field
    private String name;
    @Field
    private Instant date;
    @Field
    private String description;
    @Field
    private List<String> participants;
    @Field
    private boolean nowLive;
    @DiffIgnore
    @CreatedDate
    private Instant createdAt;
    @DiffIgnore
    @LastModifiedDate
    private Instant updatedAt;
    @DiffIgnore
    @Version
    private Long version;
}
