package com.museotek.box.web.publish;

import com.museotek.box.application.publish.PublishStatus;

import java.time.Instant;
import java.util.List;

public record PublishStatusResponse(int version, int docVersion, Instant publishedAt, boolean hasUnpublishedChanges,
                                    List<String> warnings) {

    public static PublishStatusResponse from(PublishStatus status) {
        return new PublishStatusResponse(status.version(), status.docVersion(), status.publishedAt(),
                status.hasUnpublishedChanges(), status.warnings());
    }
}
