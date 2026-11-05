package com.museotek.box.application.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.experience.ExperienceVariable;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Turns a saved experience into the {@link PublishedContent} a Box plays, and lists the media and card types it uses. */
@Component
public class PublishedContentMapper {

    private final ObjectMapper objectMapper;

    public PublishedContentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PublishedContent toContent(ExperienceView view) {
        List<PublishedContent.Variable> variables = view.variables().stream()
                .map(PublishedContentMapper::variable)
                .toList();
        List<PublishedContent.Scene> scenes = view.scenes().stream()
                .map(PublishedContentMapper::scene)
                .toList();
        return new PublishedContent(PublishedContent.SCHEMA_VERSION, view.flow().getOutput().name(), variables, scenes);
    }

    public String toJson(PublishedContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the published content as JSON", e);
        }
    }

    // Every media library id the content points at: a block's content.mediaId, and an effect's media fields.
    public List<String> mediaIds(PublishedContent content) {
        Set<String> ids = new TreeSet<>();
        for (PublishedContent.Scene scene : content.scenes()) {
            for (PublishedContent.Block block : scene.blocks()) {
                addIfUuid(ids, blockMediaId(block));
            }
            for (PublishedContent.Rule rule : scene.rules()) {
                for (RuleEffect effect : rule.effects()) {
                    addIfUuid(ids, effect.media());
                    addIfUuid(ids, effect.audio());
                    addIfUuid(ids, effect.boxVideo());
                    addIfUuid(ids, effect.url());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    // Every scan object type the content points at: start cards and the cards SCAN rules wait for.
    public List<Long> cardTypeIds(PublishedContent content) {
        Set<Long> ids = new TreeSet<>();
        for (PublishedContent.Scene scene : content.scenes()) {
            if (scene.initCardTypeId() != null) {
                ids.add(scene.initCardTypeId());
            }
            for (PublishedContent.Rule rule : scene.rules()) {
                if (rule.trigger().scanObjectTypeId() != null) {
                    ids.add(rule.trigger().scanObjectTypeId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private String blockMediaId(PublishedContent.Block block) {
        try {
            JsonNode mediaId = objectMapper.readTree(block.content()).get("mediaId");
            if (mediaId == null || !mediaId.isTextual()) {
                return null;
            }
            return mediaId.asText();
        } catch (JsonProcessingException e) {
            // Can't happen: the content comes from a jsonb column.
            throw new IllegalStateException("Block " + block.blockKey() + " content is not JSON", e);
        }
    }

    private static void addIfUuid(Set<String> ids, String value) {
        if (value == null) {
            return;
        }
        try {
            ids.add(UUID.fromString(value).toString());
        } catch (IllegalArgumentException e) {
            // Not a media library id (e.g. an empty or external value): nothing to protect.
        }
    }

    private static PublishedContent.Variable variable(ExperienceVariable variable) {
        return new PublishedContent.Variable(variable.getVariableKey(), variable.getKind().name(), variable.getInitialValue());
    }

    private static PublishedContent.Scene scene(ExperienceView.SceneView sceneView) {
        Scene scene = sceneView.scene();
        List<PublishedContent.Block> blocks = sceneView.blocks().stream()
                .map(PublishedContentMapper::block)
                .toList();
        List<PublishedContent.Rule> rules = sceneView.rules().stream()
                .map(PublishedContentMapper::rule)
                .toList();
        return new PublishedContent.Scene(scene.getSceneKey(), scene.getName(), scene.getPosition(), scene.isStart(),
                idOf(scene.getInitCardType()), blocks, rules);
    }

    private static PublishedContent.Block block(Block block) {
        return new PublishedContent.Block(block.getBlockKey(), block.getType().name(), block.getPosition(), block.getContent());
    }

    private static PublishedContent.Rule rule(Rule rule) {
        PublishedContent.Trigger trigger = new PublishedContent.Trigger(
                rule.getTriggerType().name(), idOf(rule.getScanObjectType()), rule.getTriggerSeconds());
        PublishedContent.Destination destination = new PublishedContent.Destination(
                rule.getDestinationType().name(), rule.getTargetSceneKey(), rule.getTargetSceneKeys());
        return new PublishedContent.Rule(rule.getRuleKey(), rule.getPosition(), trigger, rule.getConditions(),
                rule.getEffects(), destination);
    }

    private static Long idOf(ScanObjectType type) {
        if (type == null) {
            return null;
        }
        return type.getId();
    }
}
