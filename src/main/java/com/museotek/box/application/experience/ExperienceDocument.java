package com.museotek.box.application.experience;

import com.museotek.box.domain.rule.RuleCondition;
import com.museotek.box.domain.rule.RuleEffect;

import java.util.List;

/**
 * The shape a client sends/receives for a whole experience write — the write-side
 * counterpart to {@link ExperienceView}, entity-independent since new rows have no id yet
 * and everything is addressed by its stable string key instead.
 */
public record ExperienceDocument(
        String output,
        FlowDocument flow,
        List<VariableDocument> variables,
        List<SceneDocument> scenes
) {

    // content is the frontend's own JSON object, kept as a string: never interpreted here.
    public record FlowDocument(Integer schemaVersion, String content) {
    }

    public record VariableDocument(String key, String kind, String initial) {
    }

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

    // mediaId: the media library item the block shows (IMAGE/VIDEO blocks), lifted out of content by the web layer.
    public record BlockDocument(String blockKey, String type, Integer position, String content, String mediaId) {
    }

    // "When ... if ... do ... then": the flow/rule-model proposal's rule model v2.
    public record RuleDocument(
            String ruleKey,
            Integer position,
            TriggerDocument trigger,
            RuleCondition condition,
            List<RuleEffect> effects,
            DestinationDocument destination
    ) {
    }

    // scanObjectTypeId only for SCAN, seconds only for TIMER_ELAPSED.
    public record TriggerDocument(String type, Long scanObjectTypeId, Integer seconds) {
    }

    // targetSceneKey only for GO_TO.
    public record DestinationDocument(String type, String targetSceneKey) {
    }
}
