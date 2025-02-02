package com.github.viktor235.schedalertbot.compare;

import lombok.Data;

@Data
public class FieldDiff {

    private final String name;
    private final Object oldValue;
    private final Object newValue;
}
