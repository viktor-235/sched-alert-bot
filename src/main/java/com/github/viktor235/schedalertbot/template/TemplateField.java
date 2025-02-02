package com.github.viktor235.schedalertbot.template;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateField {

    private String name;
    private boolean changed;
    private Object oldValue;
    private Object newValue;
}
