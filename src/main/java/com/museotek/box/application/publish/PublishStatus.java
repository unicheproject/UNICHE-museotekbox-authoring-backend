package com.museotek.box.application.publish;

import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;

import java.time.Instant;

/** The latest published copy of an experience, and whether the draft has changed since. */
public record PublishStatus(int version, int docVersion, Instant publishedAt, boolean hasUnpublishedChanges) {

    public static PublishStatus of(PublishedExperience latest, Project project) {
        return new PublishStatus(latest.getVersion(), latest.getDocVersion(), latest.getPublishedAt(),
                project.getDocVersion() != latest.getDocVersion());
    }
}
