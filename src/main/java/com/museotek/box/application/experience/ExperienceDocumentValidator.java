package com.museotek.box.application.experience;

import com.museotek.box.application.experience.ExperienceDocument.BlockDocument;
import com.museotek.box.application.experience.ExperienceDocument.DestinationDocument;
import com.museotek.box.application.experience.ExperienceDocument.RuleDocument;
import com.museotek.box.application.experience.ExperienceDocument.SceneDocument;
import com.museotek.box.application.experience.ExperienceDocument.TriggerDocument;
import com.museotek.box.application.experience.ExperienceDocument.VariableDocument;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.experience.VariableKind;
import com.museotek.box.domain.rule.RuleCondition;
import com.museotek.box.domain.rule.RuleDestination;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.rule.RuleTrigger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The write algorithm's steps 1-3 (see the document-model proposal doc): validate keys,
 * validate the reference graph, validate freshness. Plus the flow and the variables (see the
 * flow/rule-model proposal doc). Pure — takes the document plus
 * everything it needs to know about current project state as plain data, touches no
 * repository itself. Step 4 (diff + apply) and step 5 (bump version) are a separate,
 * persistence-owning class.
 */
@Service
public class ExperienceDocumentValidator {

    // scene_key/block_key/rule_key are plain positive-integer strings (no leading zeros).
    private static final Pattern KEY_PATTERN = Pattern.compile("^[1-9]\\d*$");

    // Variable keys are names the frontend picks ("correct", "found"), not counter-issued numbers.
    private static final Pattern VARIABLE_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d{1,9}$");

    // Condition and effect types (see RuleCondition / RuleEffect for what each one needs).
    private static final String CONDITION_VAR_CMP = "VAR_CMP";
    private static final String CONDITION_FLAG_IS = "FLAG_IS";
    private static final Set<String> NUMBER_OPS = Set.of("GTE", "LTE", "GT", "LT", "EQ", "NEQ");
    private static final String EFFECT_REPLY = "REPLY";
    private static final String EFFECT_BOX_SCREEN = "BOX_SCREEN";
    private static final String EFFECT_BOX_AUDIO = "BOX_AUDIO";
    private static final String EFFECT_SET_NUMBER = "SET_NUMBER";
    private static final String EFFECT_ADD_NUMBER = "ADD_NUMBER";
    private static final String EFFECT_SET_FLAG = "SET_FLAG";
    private static final List<String> EFFECT_TYPES = List.of(
            EFFECT_REPLY, EFFECT_BOX_SCREEN, EFFECT_BOX_AUDIO, EFFECT_SET_NUMBER, EFFECT_ADD_NUMBER, EFFECT_SET_FLAG);

    // Flow schema versions this backend accepts. Bump together with the frontend's flow format.
    private static final Set<Integer> SUPPORTED_FLOW_SCHEMA_VERSIONS = Set.of(1);

    public ExperienceValidationResult validate(ExperienceDocument document, ExperienceWriteContext context) {
        List<String> errors = new ArrayList<>();
        validateOutput(document, context, errors);
        validateFlow(document, errors);
        validateVariables(document, errors);
        validateKeys(document, errors);
        validateGraph(document, errors);
        validateFreshness(document, context, errors);
        return new ExperienceValidationResult(errors);
    }

    // Fixed when the experience is created: every media choice depends on it, so a later save
    // may repeat it but never change it.
    private void validateOutput(ExperienceDocument document, ExperienceWriteContext context, List<String> errors) {
        String output = document.output();
        if (output == null || !isExperienceOutput(output)) {
            errors.add("output '" + output + "' is not valid (expected DISPLAY or BOX)");
            return;
        }
        String existingOutput = context.existingOutput();
        if (existingOutput != null && !existingOutput.equals(output)) {
            errors.add("output can't be changed after the experience is created (it is " + existingOutput + ")");
        }
    }

    private boolean isExperienceOutput(String value) {
        for (ExperienceOutput output : ExperienceOutput.values()) {
            if (output.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    // The flow is the frontend's own source and isn't interpreted here: it only has to be there,
    // with a schema version this backend knows. "Is a JSON object" is enforced by the web layer.
    private void validateFlow(ExperienceDocument document, List<String> errors) {
        if (document.flow() == null || document.flow().content() == null) {
            errors.add("flow is required: the flow and the graph are always saved together");
            return;
        }
        Integer schemaVersion = document.flow().schemaVersion();
        if (schemaVersion == null || !SUPPORTED_FLOW_SCHEMA_VERSIONS.contains(schemaVersion)) {
            errors.add("flow schemaVersion '" + schemaVersion + "' is not supported (supported: "
                    + SUPPORTED_FLOW_SCHEMA_VERSIONS + ")");
        }
    }

    private void validateVariables(ExperienceDocument document, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (VariableDocument variable : document.variables()) {
            String key = variable.key();
            if (key == null || !VARIABLE_KEY_PATTERN.matcher(key).matches()) {
                errors.add("variable key '" + key + "' is not well-formed (expected a letter, then letters, digits or _)");
                continue;
            }
            if (!seen.add(key)) {
                errors.add("variable key '" + key + "' is used more than once in this document");
            }
            VariableKind kind = parseVariableKind(variable.kind());
            if (kind == null) {
                errors.add("variable '" + key + "' kind '" + variable.kind() + "' is not valid (expected NUMBER or FLAG)");
                continue;
            }
            if (!isValidInitial(kind, variable.initial())) {
                errors.add("variable '" + key + "' initial value '" + variable.initial() + "' doesn't match its kind "
                        + kind + (kind == VariableKind.NUMBER ? " (expected an integer)" : " (expected true or false)"));
            }
        }
    }

    private VariableKind parseVariableKind(String kind) {
        return parseEnum(VariableKind.class, kind);
    }

    private boolean isValidInitial(VariableKind kind, String initial) {
        if (initial == null) {
            return false;
        }
        if (kind == VariableKind.NUMBER) {
            return INTEGER_PATTERN.matcher(initial).matches();
        }
        return initial.equals("true") || initial.equals("false");
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
        int startSceneCount = 0;
        for (SceneDocument scene : document.scenes()) {
            if (scene.isStart()) {
                startSceneCount++;
            }
        }
        if (startSceneCount != 1) {
            errors.add("exactly one scene must have is_start = true, found " + startSceneCount);
        }
        validateRules(document, errors);
    }

    // Rule model v2: when (trigger) ... if (condition) ... do (effects, in order) ... then
    // (destination). Only structure is refused here. A gap the curator still has to fill (a SCAN
    // with no card chosen yet, a VIDEO_ENDED on a scene without a video) is not an error.
    private void validateRules(ExperienceDocument document, List<String> errors) {
        Set<String> allSceneKeys = new HashSet<>();
        for (SceneDocument scene : document.scenes()) {
            allSceneKeys.add(scene.sceneKey());
        }
        Map<String, VariableKind> declaredVariables = declaredVariables(document);

        for (SceneDocument scene : document.scenes()) {
            // Of the rules whose trigger matches, the first by position runs - so a catch-all
            // (SCAN_OTHER) placed before another scan rule makes that rule unreachable.
            List<RuleDocument> byPosition = scene.rules().stream()
                    .sorted(Comparator.comparing(RuleDocument::position, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            String catchAllRuleKey = null;
            for (RuleDocument rule : byPosition) {
                String label = "rule '" + rule.ruleKey() + "'";
                RuleTrigger trigger = validateTrigger(rule.trigger(), scene, label, errors);
                if (trigger == RuleTrigger.SCAN || trigger == RuleTrigger.SCAN_OTHER) {
                    if (catchAllRuleKey != null) {
                        errors.add(label + " can never run: the catch-all rule '" + catchAllRuleKey
                                + "' (SCAN_OTHER) comes before it in scene '" + scene.sceneKey() + "'");
                    } else if (trigger == RuleTrigger.SCAN_OTHER) {
                        catchAllRuleKey = rule.ruleKey();
                    }
                }
                validateCondition(rule.condition(), declaredVariables, label, errors);
                validateEffects(rule.effects(), declaredVariables, label, errors);
                validateDestination(rule.destination(), allSceneKeys, label, errors);
            }
        }
    }

    // Only the variables that are themselves valid: a broken declaration is already reported by
    // validateVariables, and shouldn't also produce an "undeclared" error on every use.
    private Map<String, VariableKind> declaredVariables(ExperienceDocument document) {
        Map<String, VariableKind> declared = new HashMap<>();
        for (VariableDocument variable : document.variables()) {
            VariableKind kind = parseVariableKind(variable.kind());
            if (variable.key() != null && kind != null) {
                declared.putIfAbsent(variable.key(), kind);
            }
        }
        return declared;
    }

    private RuleTrigger validateTrigger(TriggerDocument trigger, SceneDocument scene, String label, List<String> errors) {
        if (trigger == null) {
            errors.add(label + " has no trigger");
            return null;
        }
        RuleTrigger type = parseEnum(RuleTrigger.class, trigger.type());
        if (type == null) {
            errors.add(label + " trigger '" + trigger.type() + "' is not valid (expected one of "
                    + List.of(RuleTrigger.values()) + ")");
            return null;
        }
        if (type == RuleTrigger.TIMER_ELAPSED && (trigger.seconds() == null || trigger.seconds() <= 0)) {
            errors.add(label + " trigger TIMER_ELAPSED needs seconds > 0");
        }
        if (type != RuleTrigger.TIMER_ELAPSED && trigger.seconds() != null) {
            errors.add(label + " trigger " + type + " can't have seconds (only TIMER_ELAPSED)");
        }
        if (type != RuleTrigger.SCAN && trigger.scanObjectTypeId() != null) {
            errors.add(label + " trigger " + type + " can't have a scan object type (only SCAN)");
        }
        if (type == RuleTrigger.SESSION_STARTED && !scene.isStart()) {
            errors.add(label + " trigger SESSION_STARTED is only allowed on the start scene");
        }
        return type;
    }

    private void validateCondition(RuleCondition condition, Map<String, VariableKind> declared, String label, List<String> errors) {
        if (condition == null) {
            return; // no condition = always
        }
        if (CONDITION_VAR_CMP.equals(condition.type())) {
            checkVariable(condition.variable(), VariableKind.NUMBER, declared, label + " condition", errors);
            if (condition.op() == null || !NUMBER_OPS.contains(condition.op())) {
                errors.add(label + " condition op '" + condition.op() + "' is not valid (expected one of " + NUMBER_OPS + ")");
            }
            if (condition.value() == null || !INTEGER_PATTERN.matcher(condition.value()).matches()) {
                errors.add(label + " condition value '" + condition.value() + "' is not an integer");
            }
        } else if (CONDITION_FLAG_IS.equals(condition.type())) {
            checkVariable(condition.variable(), VariableKind.FLAG, declared, label + " condition", errors);
            if (!isBoolean(condition.value())) {
                errors.add(label + " condition value '" + condition.value() + "' is not true or false");
            }
        } else {
            errors.add(label + " condition type '" + condition.type() + "' is not valid (expected VAR_CMP or FLAG_IS)");
        }
    }

    private void validateEffects(List<RuleEffect> effects, Map<String, VariableKind> declared, String label, List<String> errors) {
        if (effects == null) {
            errors.add(label + " has no effects list (send [] for none)");
            return;
        }
        for (int i = 0; i < effects.size(); i++) {
            RuleEffect effect = effects.get(i);
            String effectLabel = label + " effect " + (i + 1);
            if (effect == null) {
                errors.add(effectLabel + " is empty");
                continue;
            }
            String type = effect.type();
            if (EFFECT_REPLY.equals(type)) {
                if (isBlank(effect.color())) {
                    errors.add(effectLabel + " REPLY needs a color");
                }
            } else if (EFFECT_BOX_SCREEN.equals(type)) {
                if (isBlank(effect.text())) {
                    errors.add(effectLabel + " BOX_SCREEN needs a text");
                }
            } else if (EFFECT_BOX_AUDIO.equals(type)) {
                // the sound may still be unpicked: a gap, not a structural error
            } else if (EFFECT_SET_NUMBER.equals(type)) {
                checkVariable(effect.variable(), VariableKind.NUMBER, declared, effectLabel, errors);
                if (effect.value() == null || !INTEGER_PATTERN.matcher(effect.value()).matches()) {
                    errors.add(effectLabel + " SET_NUMBER value '" + effect.value() + "' is not an integer");
                }
            } else if (EFFECT_ADD_NUMBER.equals(type)) {
                checkVariable(effect.variable(), VariableKind.NUMBER, declared, effectLabel, errors);
                if (effect.amount() == null) {
                    errors.add(effectLabel + " ADD_NUMBER needs an amount");
                }
            } else if (EFFECT_SET_FLAG.equals(type)) {
                checkVariable(effect.variable(), VariableKind.FLAG, declared, effectLabel, errors);
                if (!isBoolean(effect.value())) {
                    errors.add(effectLabel + " SET_FLAG value '" + effect.value() + "' is not true or false");
                }
            } else {
                errors.add(effectLabel + " type '" + type + "' is not valid (expected one of " + EFFECT_TYPES + ")");
            }
        }
    }

    private void validateDestination(DestinationDocument destination, Set<String> allSceneKeys, String label, List<String> errors) {
        if (destination == null) {
            errors.add(label + " has no destination");
            return;
        }
        RuleDestination type = parseEnum(RuleDestination.class, destination.type());
        if (type == null) {
            errors.add(label + " destination '" + destination.type() + "' is not valid (expected GO_TO, STAY or END)");
            return;
        }
        if (type == RuleDestination.GO_TO) {
            if (destination.targetSceneKey() == null || !allSceneKeys.contains(destination.targetSceneKey())) {
                errors.add(label + " target_scene_key '" + destination.targetSceneKey()
                        + "' does not resolve to any scene in this document");
            }
        } else if (destination.targetSceneKey() != null) {
            errors.add(label + " destination " + type + " can't have a target scene (only GO_TO)");
        }
    }

    private void checkVariable(String key, VariableKind expected, Map<String, VariableKind> declared, String label, List<String> errors) {
        VariableKind actual = declared.get(key);
        if (actual == null) {
            errors.add(label + " uses variable '" + key + "', which is not declared");
        } else if (actual != expected) {
            errors.add(label + " needs a " + expected + " variable, but '" + key + "' is a " + actual);
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equals(value) || "false".equals(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
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
