package com.museotek.box.domain.experience;

import java.util.UUID;

/**
 * The If-Match header didn't match the project's current doc_version. Maps to 409 in
 * {@code ExperienceController} itself, not {@code GlobalExceptionHandler} — the response body
 * must be the current document (per the document-model proposal's write algorithm), not a
 * generic error envelope.
 */
public class StaleExperienceVersionException extends RuntimeException {

    private final UUID projectId;

    public StaleExperienceVersionException(UUID projectId, int expectedVersion, int actualVersion) {
        super("Experience document for project " + projectId + " is at version " + actualVersion
                + ", but If-Match sent " + expectedVersion);
        this.projectId = projectId;
    }

    public UUID getProjectId() {
        return projectId;
    }
}
