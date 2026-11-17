package com.museotek.box.application.player;

import com.museotek.box.domain.media.MediaKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One experience's latest published copy as a Box plays it: the content and the media files it needs. Plain values
 * only, no entities.
 */
public record PlayerExperienceCopy(PlayerExperience experience, Instant publishedAt, String content,
                                   List<MediaFile> media) {

    /** {@code sha256} is null only for an old file the startup backfill hasn't reached (or whose file is missing). */
    public record MediaFile(UUID id, MediaKind kind, String contentType, long size, String sha256) {
    }

    public PlayerExperienceCopy {
        media = List.copyOf(media);
    }
}
