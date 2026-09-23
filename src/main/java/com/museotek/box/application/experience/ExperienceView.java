package com.museotek.box.application.experience;

import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.scene.Scene;

import java.util.List;
import java.util.UUID;

/**
 * A project's whole experience as one document: every scene, with its own blocks and rules
 * grouped under it, plus the version the client must echo back as {@code If-Match} to write.
 */
public record ExperienceView(UUID projectId, int version, List<SceneView> scenes) {

    public record SceneView(Scene scene, List<Block> blocks, List<Rule> rules) {
    }
}
