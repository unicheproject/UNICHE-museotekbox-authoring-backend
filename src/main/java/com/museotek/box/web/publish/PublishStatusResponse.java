package com.museotek.box.web.publish;

import com.museotek.box.application.publish.PublishStatus;

import java.time.Instant;

public record PublishStatusResponse(int version, int docVersion, Instant publishedAt, boolean hasUnpublishedChanges) {

    public static PublishStatusResponse from(PublishStatus status) {
        return new PublishStatusResponse(status.version(), status.docVersion(), status.publishedAt(), status.hasUnpublishedChanges());
    }
}
