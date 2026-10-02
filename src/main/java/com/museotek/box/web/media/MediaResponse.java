package com.museotek.box.web.media;

import com.museotek.box.domain.media.Media;

import java.time.Instant;
import java.util.UUID;

// contentUrl is a path on this backend (context path included), fetched with the user's bearer token like any call.
public record MediaResponse(
        UUID id,
        String kind,
        String name,
        String contentType,
        long sizeBytes,
        String contentUrl,
        Instant uploadedAt
) {

    public static MediaResponse from(Media media, String contextPath) {
        String contentUrl = contextPath + "/api/v1/organisations/" + media.getOrgId() + "/media/" + media.getId() + "/content";
        return new MediaResponse(media.getId(), media.getKind().name(), media.getName(), media.getContentType(),
                media.getSizeBytes(), contentUrl, media.getUploadedAt());
    }
}
