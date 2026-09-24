package com.museotek.box.web.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.application.experience.ExperienceDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Mirrors ExperienceDocument's shape field-for-field (see that record's own note on why
// type/eventType/action stay plain strings here too, resolved into real enums only once the
// write actually applies). content is bound as a JsonNode, not a String, since the client sends
// it as a nested JSON value, not a pre-escaped string - toDocument() serialises it back to text
// for the jsonb column.
public record ExperienceWriteRequest(@NotNull @Valid List<SceneRequest> scenes) {

    public record SceneRequest(
            @NotBlank String sceneKey,
            @NotBlank String name,
            @NotNull Integer position,
            boolean isStart,
            Long initCardTypeId,
            @NotNull @Valid List<BlockRequest> blocks,
            @NotNull @Valid List<RuleRequest> rules
    ) {
        ExperienceDocument.SceneDocument toDocument() {
            List<ExperienceDocument.BlockDocument> blockDocuments = blocks.stream()
                    .map(BlockRequest::toDocument)
                    .toList();
            List<ExperienceDocument.RuleDocument> ruleDocuments = rules.stream()
                    .map(RuleRequest::toDocument)
                    .toList();
            return new ExperienceDocument.SceneDocument(
                    sceneKey, name, position, isStart, initCardTypeId, blockDocuments, ruleDocuments);
        }
    }

    public record BlockRequest(
            @NotBlank String blockKey,
            @NotBlank String type,
            @NotNull Integer position,
            @NotNull JsonNode content
    ) {
        ExperienceDocument.BlockDocument toDocument() {
            return new ExperienceDocument.BlockDocument(blockKey, type, position, content.toString());
        }
    }

    public record RuleRequest(
            @NotBlank String ruleKey,
            @NotBlank String eventType,
            @NotBlank String action,
            @NotNull Integer position,
            Long scanObjectTypeId,
            String triggerBlockKey,
            String targetSceneKey,
            String targetBlockKey
    ) {
        ExperienceDocument.RuleDocument toDocument() {
            return new ExperienceDocument.RuleDocument(
                    ruleKey, eventType, action, position, scanObjectTypeId,
                    triggerBlockKey, targetSceneKey, targetBlockKey);
        }
    }

    public ExperienceDocument toDocument() {
        return new ExperienceDocument(scenes.stream().map(SceneRequest::toDocument).toList());
    }
}
