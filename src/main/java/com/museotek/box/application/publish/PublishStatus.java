package com.museotek.box.application.publish;

import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;

import java.time.Instant;
import java.util.List;

/**
 * The latest published copy of an experience, whether the draft has changed since, and what is
 * worth fixing without blocking a publish ({@link PublishWarnings}).
 */
public record PublishStatus(int version, int docVersion, Instant publishedAt, boolean hasUnpublishedChanges,
                            List<String> warnings) {

    public static PublishStatus of(PublishedExperience latest, Project project, List<String> warnings) {
        return new PublishStatus(latest.getVersion(), latest.getDocVersion(), latest.getPublishedAt(),
                project.getDocVersion() != latest.getDocVersion(), List.copyOf(warnings));
    }
}
