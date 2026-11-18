package com.museotek.box.web.boxapi;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.museotek.box.application.player.PlayerExperienceCopy;
import com.museotek.box.domain.experience.ExperienceOutput;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One experience's latest copy, as a Box downloads it. {@code content} is the stored PublishedContent
 * JSON, passed through as JSON rather than as a string.
 */
public record BoxExperienceResponse(UUID projectId, String name, int version, ExperienceOutput output,
                                       Long startCardTypeId, Instant publishedAt, @JsonRawValue String content,
                                       List<PlayerExperienceCopy.MediaFile> media) {

    public static BoxExperienceResponse from(PlayerExperienceCopy copy) {
        return new BoxExperienceResponse(
                copy.experience().projectId(),
                copy.experience().name(),
                copy.experience().version(),
                copy.experience().output(),
                copy.experience().startCardTypeId(),
                copy.publishedAt(),
                copy.content(),
                copy.media());
    }
}
