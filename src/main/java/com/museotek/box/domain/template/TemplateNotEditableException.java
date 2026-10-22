package com.museotek.box.domain.template;

/** An attempt to change or delete a global template, which only changes through Liquibase. Maps to 403. */
public class TemplateNotEditableException extends RuntimeException {
    public TemplateNotEditableException(String message) {
        super(message);
    }
}
