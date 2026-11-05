package com.museotek.box.domain.publish;

/** The experience has never been published. Maps to 404 in GlobalExceptionHandler. */
public class ExperienceNotPublishedException extends RuntimeException {
    public ExperienceNotPublishedException(String message) {
        super(message);
    }
}
