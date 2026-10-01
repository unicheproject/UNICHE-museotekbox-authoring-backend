package com.museotek.box.application.experience;

import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.experience.ExperienceFlow;
import com.museotek.box.domain.experience.ExperienceVariable;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.scene.Scene;

import java.util.List;
import java.util.UUID;

/**
 * A project's whole experience as one document: every scene, with its own blocks and rules
 * grouped under it, plus the version the client must echo back as {@code If-Match} to write.
 * The three {@code next*Seq} counters are exposed so a client can invent valid new
 * scene/block/rule keys for its next write without guessing from the existing keys alone.
 */
public record ExperienceView(
        UUID projectId,
        int version,
        int nextSceneSeq,
        int nextBlockSeq,
        int nextRuleSeq,
        ExperienceFlow flow,
        List<ExperienceVariable> variables,
        List<SceneView> scenes
) {

    public record SceneView(Scene scene, List<Block> blocks, List<Rule> rules) {
    }
}
