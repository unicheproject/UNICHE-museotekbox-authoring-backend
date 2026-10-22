package com.museotek.box.domain.template;

/** No template with this id that the organisation can see. Maps to 404 in GlobalExceptionHandler. */
public class ExperienceTemplateNotFoundException extends RuntimeException {
    public ExperienceTemplateNotFoundException(String message) {
        super(message);
    }
}
