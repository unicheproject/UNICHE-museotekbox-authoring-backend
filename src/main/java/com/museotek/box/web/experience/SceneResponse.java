package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;

import java.util.List;

public record SceneResponse(
        String sceneKey,
        String name,
        Integer position,
        boolean isStart,
        Long initCardTypeId,
        List<BlockResponse> blocks,
        List<RuleResponse> rules
) {

    public static SceneResponse from(ExperienceView.SceneView sceneView) {
        Scene scene = sceneView.scene();

        ScanObjectType initCardType = scene.getInitCardType();
        Long initCardTypeId;
        if (initCardType != null) {
            initCardTypeId = initCardType.getId();
        } else {
            initCardTypeId = null;
        }

        List<BlockResponse> blocks = sceneView.blocks().stream()
                .map(BlockResponse::from)
                .toList();
        List<RuleResponse> rules = sceneView.rules().stream()
                .map(RuleResponse::from)
                .toList();

        return new SceneResponse(scene.getSceneKey(), scene.getName(), scene.getPosition(), scene.isStart(),
                initCardTypeId, blocks, rules);
    }
}
