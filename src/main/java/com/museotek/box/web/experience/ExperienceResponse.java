package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceView;

import java.util.List;
import java.util.UUID;

public record ExperienceResponse(UUID projectId, int version, List<SceneResponse> scenes) {

    public static ExperienceResponse from(ExperienceView view) {
        List<SceneResponse> scenes = view.scenes().stream()
                .map(SceneResponse::from)
                .toList();
        return new ExperienceResponse(view.projectId(), view.version(), scenes);
    }
}
