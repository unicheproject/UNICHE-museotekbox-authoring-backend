package com.museotek.box.application.experience;

import java.util.List;

/**
 * The shape a client sends/receives for a whole experience write — the write-side
 * counterpart to {@link ExperienceView}, entity-independent since new rows have no id yet
 * and everything is addressed by its stable string key instead.
 */
public record ExperienceDocument(List<SceneDocument> scenes) {

    public record SceneDocument(
            String sceneKey,
            String name,
            Integer position,
            boolean isStart,
            Long initCardTypeId,
            List<BlockDocument> blocks,
            List<RuleDocument> rules
    ) {
    }

    public record BlockDocument(String blockKey, String type, Integer position, String content) {
    }

    public record RuleDocument(
            String ruleKey,
            String eventType,
            String action,
            Integer position,
            Long scanObjectTypeId,
            String triggerBlockKey,
            String targetSceneKey,
            String targetBlockKey
    ) {
    }
}
