package com.museotek.box.domain.experience;

import java.util.List;

/** The write's document-model validation failed. Maps to 400 in GlobalExceptionHandler. */
public class ExperienceValidationException extends RuntimeException {

    private final List<String> errors;

    public ExperienceValidationException(List<String> errors) {
        super("Experience document failed validation: " + errors);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
