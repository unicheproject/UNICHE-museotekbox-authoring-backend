package com.museotek.box.application.experience;

import com.museotek.box.application.experience.ExperienceDocument.BlockDocument;
import com.museotek.box.application.experience.ExperienceDocument.RuleDocument;
import com.museotek.box.application.experience.ExperienceDocument.SceneDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The write algorithm's steps 1-3 (see the document-model proposal doc): validate keys,
 * validate the reference graph, validate freshness. Pure — takes the document plus
 * everything it needs to know about current project state as plain data, touches no
 * repository itself. Step 4 (diff + apply) and step 5 (bump version) are a separate,
 * persistence-owning class.
 */
@Service
public class ExperienceDocumentValidator {

    // scene_key/block_key/rule_key are plain positive-integer strings (no leading zeros).
    private static final Pattern KEY_PATTERN = Pattern.compile("^[1-9]\\d*$");

    public ExperienceValidationResult validate(ExperienceDocument document, ExperienceWriteContext context) {
        List<String> errors = new ArrayList<>();
        validateKeys(document, errors);
        validateGraph(document, errors);
        validateFreshness(document, context, errors);
        return new ExperienceValidationResult(errors);
    }

    // Every key must be well-formed, and each of scene_key/block_key/rule_key must be
    // unique across the whole document - each type draws from one project-wide counter,
    // not one per scene, so a repeat in a different scene is still a collision.
    private void validateKeys(ExperienceDocument document, List<String> errors) {
        Set<String> seenSceneKeys = new HashSet<>();
        Set<String> seenBlockKeys = new HashSet<>();
        Set<String> seenRuleKeys = new HashSet<>();

        for (SceneDocument scene : document.scenes()) {
            checkKey("scene_key", scene.sceneKey(), seenSceneKeys, errors);
            for (BlockDocument block : scene.blocks()) {
                checkKey("block_key", block.blockKey(), seenBlockKeys, errors);
            }
            for (RuleDocument rule : scene.rules()) {
                checkKey("rule_key", rule.ruleKey(), seenRuleKeys, errors);
            }
        }
    }

    private void checkKey(String fieldName, String key, Set<String> seenSoFar, List<String> errors) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            errors.add(fieldName + " '" + key + "' is not well-formed (expected a positive integer string)");
            return;
        }
        if (!seenSoFar.add(key)) {
            errors.add(fieldName + " '" + key + "' is used more than once in this document");
        }
    }

    // Every target_scene_key/target_block_key/trigger_block_key must resolve to something
    // in the document, and exactly one scene must be the start scene.
    private void validateGraph(ExperienceDocument document, List<String> errors) {
        Set<String> allSceneKeys = new HashSet<>();
        for (SceneDocument scene : document.scenes()) {
            allSceneKeys.add(scene.sceneKey());
        }

        int startSceneCount = 0;
        for (SceneDocument scene : document.scenes()) {
            if (scene.isStart()) {
                startSceneCount++;
            }

            // trigger_block_key/target_block_key name a block in the rule's OWN scene, not
            // anywhere in the project - a rule reacts to / shows a block of the scene it lives in.
            Set<String> ownBlockKeys = new HashSet<>();
            for (BlockDocument block : scene.blocks()) {
                ownBlockKeys.add(block.blockKey());
            }

            for (RuleDocument rule : scene.rules()) {
                if (rule.targetSceneKey() != null && !allSceneKeys.contains(rule.targetSceneKey())) {
                    errors.add("rule '" + rule.ruleKey() + "' target_scene_key '" + rule.targetSceneKey()
                            + "' does not resolve to any scene in this document");
                }
                if (rule.targetBlockKey() != null && !ownBlockKeys.contains(rule.targetBlockKey())) {
                    errors.add("rule '" + rule.ruleKey() + "' target_block_key '" + rule.targetBlockKey()
                            + "' does not resolve to a block in the same scene ('" + scene.sceneKey() + "')");
                }
                if (rule.triggerBlockKey() != null && !ownBlockKeys.contains(rule.triggerBlockKey())) {
                    errors.add("rule '" + rule.ruleKey() + "' trigger_block_key '" + rule.triggerBlockKey()
                            + "' does not resolve to a block in the same scene ('" + scene.sceneKey() + "')");
                }
            }
        }

        if (startSceneCount != 1) {
            errors.add("exactly one scene must have is_start = true, found " + startSceneCount);
        }
    }

    // A key already present in the project (an update) is exempt - only a genuinely new key
    // has to be ahead of the project's running counter for that type.
    private void validateFreshness(ExperienceDocument document, ExperienceWriteContext context, List<String> errors) {
        for (SceneDocument scene : document.scenes()) {
            checkFreshness("scene_key", scene.sceneKey(), context.existingSceneKeys(), context.nextSceneSeq(), errors);
            for (BlockDocument block : scene.blocks()) {
                checkFreshness("block_key", block.blockKey(), context.existingBlockKeys(), context.nextBlockSeq(), errors);
            }
            for (RuleDocument rule : scene.rules()) {
                checkFreshness("rule_key", rule.ruleKey(), context.existingRuleKeys(), context.nextRuleSeq(), errors);
            }
        }
    }

    private void checkFreshness(String fieldName, String key, Set<String> existingKeys, int nextSeq, List<String> errors) {
        if (key == null || !KEY_PATTERN.matcher(key).matches() || existingKeys.contains(key)) {
            return; // malformed already reported by validateKeys; an existing key is always fresh
        }
        int value = Integer.parseInt(key);
        if (value < nextSeq) {
            errors.add(fieldName + " '" + key + "' is a new key but is behind the project's counter (next is "
                    + nextSeq + ")");
        }
    }
}
