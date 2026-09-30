package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceView;

import java.util.List;
import java.util.UUID;

/**
 * The experience content of one project: scenes, blocks, rules, the document version and the
 * next free keys. Carries none of the project's item data (name, status). That is
 * {@code ProjectResponse}, for the same project id.
 */
public record ExperienceResponse(
        UUID projectId,
        int version,
        int nextSceneSeq,
        int nextBlockSeq,
        int nextRuleSeq,
        List<SceneResponse> scenes
) {

    public static ExperienceResponse from(ExperienceView view) {
        List<SceneResponse> scenes = view.scenes().stream()
                .map(SceneResponse::from)
                .toList();
        return new ExperienceResponse(view.projectId(), view.version(), view.nextSceneSeq(), view.nextBlockSeq(),
                view.nextRuleSeq(), scenes);
    }
}
