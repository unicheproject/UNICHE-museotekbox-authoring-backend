package com.museotek.box.web.player;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.museotek.box.application.player.PlayerExperienceCopy;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code content} is the stored PublishedContent JSON, passed through as JSON rather than as a string. */
public record PlayerExperienceResponse(UUID projectId, String name, int version, ExperienceOutput output,
                                       Long startCardTypeId, Instant publishedAt, @JsonRawValue String content,
                                       List<MediaFile> media) {

    public record MediaFile(UUID id, MediaKind kind, String contentType, long size) {

        static MediaFile from(Media media) {
            return new MediaFile(media.getId(), media.getKind(), media.getContentType(), media.getSizeBytes());
        }
    }

    public static PlayerExperienceResponse from(PlayerExperienceCopy copy) {
        return new PlayerExperienceResponse(
                copy.experience().projectId(),
                copy.experience().name(),
                copy.experience().version(),
                copy.experience().output(),
                copy.experience().startCardTypeId(),
                copy.publishedAt(),
                copy.content(),
                copy.media().stream().map(MediaFile::from).toList());
    }
}
