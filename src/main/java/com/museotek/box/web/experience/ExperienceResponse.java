package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceView;

import java.util.List;
import java.util.UUID;

/**
 * The experience content of one project: where it plays ({@code output}, {@code null} until the
 * first save), the flow (the curator's source, {@code null} until the
 * first save), the variables, the scenes/blocks/rules, the document version and the next free
 * keys. Carries none of the project's item data (name, status). That is
 * {@code ProjectResponse}, for the same project id.
 */
public record ExperienceResponse(
        UUID projectId,
        int version,
        int nextSceneSeq,
        int nextBlockSeq,
        int nextRuleSeq,
        String output,
        FlowResponse flow,
        List<VariableResponse> variables,
        List<SceneResponse> scenes
) {

    public static ExperienceResponse from(ExperienceView view) {
        List<VariableResponse> variables = view.variables().stream()
                .map(VariableResponse::from)
                .toList();
        List<SceneResponse> scenes = view.scenes().stream()
                .map(SceneResponse::from)
                .toList();
        return new ExperienceResponse(view.projectId(), view.version(), view.nextSceneSeq(), view.nextBlockSeq(),
                view.nextRuleSeq(), outputOf(view), FlowResponse.from(view.flow()), variables, scenes);
    }

    private static String outputOf(ExperienceView view) {
        if (view.flow() == null) {
            return null;
        }
        return view.flow().getOutput().name();
    }
}
