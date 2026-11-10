package com.museotek.box.application.player;

import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.publish.PublishedExperience;

import java.util.UUID;

/** The latest published copy of an experience, without its content: what a Box can start and with which card. */
public record PlayerExperience(UUID projectId, String name, int version, ExperienceOutput output, Long startCardTypeId) {

    static PlayerExperience of(PublishedExperience copy) {
        return new PlayerExperience(copy.getProject().getId(), copy.getName(), copy.getVersion(), copy.getOutput(),
                copy.getStartCardTypeId());
    }
}
