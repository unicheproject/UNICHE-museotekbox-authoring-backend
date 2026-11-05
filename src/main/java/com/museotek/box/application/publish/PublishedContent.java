package com.museotek.box.application.publish;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.museotek.box.domain.rule.RuleCondition;
import com.museotek.box.domain.rule.RuleEffect;

import java.util.List;

/**
 * What a Box gets of a published experience: only what it needs to play it. Deliberately its own
 * format, not the CMS's experience response: no wizard flow, no key counters, and a change to the
 * CMS response can't change what Boxes receive. A breaking change raises {@link #SCHEMA_VERSION}.
 */
public record PublishedContent(int contentSchemaVersion, String output, List<Variable> variables, List<Scene> scenes) {

    public static final int SCHEMA_VERSION = 1;

    public record Variable(String key, String kind, String initial) {
    }

    public record Scene(String sceneKey, String name, int position, boolean isStart, Long initCardTypeId,
                        List<Block> blocks, List<Rule> rules) {
    }

    // content is the block's jsonb as is, embedded as JSON rather than an escaped string.
    public record Block(String blockKey, String type, int position, @JsonRawValue String content) {
    }

    public record Rule(String ruleKey, int position, Trigger trigger, List<RuleCondition> conditions,
                       List<RuleEffect> effects, Destination destination) {
    }

    public record Trigger(String type, Long scanObjectTypeId, Integer seconds) {
    }

    public record Destination(String type, String targetSceneKey, List<String> targetSceneKeys) {
    }
}
