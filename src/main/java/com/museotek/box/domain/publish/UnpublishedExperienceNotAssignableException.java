package com.museotek.box.domain.publish;

/**
 * Assigning an experience that has never been published: a Box only plays published copies.
 * Same error code as its parent, but 422 instead of 404, since the request names a real experience.
 */
public class UnpublishedExperienceNotAssignableException extends ExperienceNotPublishedException {
    public UnpublishedExperienceNotAssignableException(String message) {
        super(message);
    }
}
