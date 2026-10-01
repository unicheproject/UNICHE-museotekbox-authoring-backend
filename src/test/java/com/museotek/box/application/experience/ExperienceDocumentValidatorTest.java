package com.museotek.box.application.experience;

import com.museotek.box.application.experience.ExperienceDocument.BlockDocument;
import com.museotek.box.application.experience.ExperienceDocument.DestinationDocument;
import com.museotek.box.application.experience.ExperienceDocument.FlowDocument;
import com.museotek.box.application.experience.ExperienceDocument.RuleDocument;
import com.museotek.box.application.experience.ExperienceDocument.SceneDocument;
import com.museotek.box.application.experience.ExperienceDocument.TriggerDocument;
import com.museotek.box.application.experience.ExperienceDocument.VariableDocument;
import com.museotek.box.domain.rule.RuleCondition;
import com.museotek.box.domain.rule.RuleEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceDocumentValidatorTest {

    private final ExperienceDocumentValidator validator = new ExperienceDocumentValidator();

    private static final ExperienceWriteContext EMPTY_PROJECT_CONTEXT =
            new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 1, 1, 1, null);

    private static final FlowDocument VALID_FLOW = new FlowDocument(1, "{\"details\":{},\"islands\":[]}");


    @Test
    void missingFlow_isRejected() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", null, List.of(), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("flow is required"));
    }

    @Test
    void unsupportedFlowSchemaVersion_isRejected() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", new FlowDocument(99, "{}"), List.of(), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("schemaVersion '99' is not supported"));
    }

    @Test
    void wellFormedVariables_areValid() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(
                new VariableDocument("correct", "NUMBER", "0"),
                new VariableDocument("seen_map", "FLAG", "false")), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).isEmpty();
    }

    @Test
    void malformedVariableKey_isRejected() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(
                new VariableDocument("1score", "NUMBER", "0")), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("variable key '1score' is not well-formed"));
    }

    @Test
    void duplicateVariableKey_isRejected() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(
                new VariableDocument("correct", "NUMBER", "0"),
                new VariableDocument("correct", "NUMBER", "1")), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("variable key 'correct' is used more than once"));
    }

    @Test
    void unknownVariableKind_isRejected() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(
                new VariableDocument("label", "TEXT", "hi")), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("kind 'TEXT' is not valid"));
    }

    @Test
    void initialValueNotMatchingKind_isRejected() {
        ExperienceDocument document = new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(
                new VariableDocument("correct", "NUMBER", "zero"),
                new VariableDocument("seen", "FLAG", "yes")), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors())
                .anyMatch(e -> e.contains("variable 'correct'") && e.contains("expected an integer"))
                .anyMatch(e -> e.contains("variable 'seen'") && e.contains("expected true or false"));
    }


    @Test
    void invalidOutput_isRejected() {
        ExperienceDocument document = new ExperienceDocument("SPEAKER", VALID_FLOW, List.of(), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("output 'SPEAKER' is not valid"));
    }

    @Test
    void outputDifferentFromStoredOne_isRejected() {
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 1, 1, 1, "DISPLAY");
        ExperienceDocument document = new ExperienceDocument("BOX", VALID_FLOW, List.of(), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.errors()).anyMatch(e -> e.contains("output can't be changed") && e.contains("it is DISPLAY"));
    }

    @Test
    void outputSameAsStoredOne_isValid() {
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 1, 1, 1, "BOX");
        ExperienceDocument document = new ExperienceDocument("BOX", VALID_FLOW, List.of(), ONE_START_SCENE);

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.errors()).isEmpty();
    }

    private static ExperienceDocument document(List<SceneDocument> scenes) {
        return new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(), scenes);
    }
    private static final List<SceneDocument> ONE_START_SCENE = List.of(new SceneDocument("1", "Start", 0, true, null, List.of(), List.of()));

    @Test
    void wellFormedNewDocument_isValid() {
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(block("1")), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void malformedKey_isRejected() {
        ExperienceDocument document = document(List.of(
                scene("01", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("scene_key") && e.contains("not well-formed"));
    }

    @Test
    void duplicateSceneKey_isRejected() {
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(), List.of()),
                scene("1", false, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("scene_key") && e.contains("used more than once"));
    }

    @Test
    void duplicateBlockKeyAcrossDifferentScenes_isRejected() {
        // block_key draws from one project-wide counter, not one per scene, so a repeat
        // in a different scene is still a collision even though the DB constraint alone
        // would allow it.
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(block("1")), List.of()),
                scene("2", false, List.of(block("1")), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("block_key") && e.contains("used more than once"));
    }

    @Test
    void goToUnknownScene_isRejected() {
        RuleDocument rule = rule("1", 0, trigger("SCENE_ENTERED"), null, List.of(), goTo("99"));
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(), List.of(rule))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("target_scene_key '99'"));
    }

    @Test
    void quizStyleRules_areValid() {
        // What the prototype generates for a question: right card -> reply + score + next,
        // anything else -> reply, stay; nobody playing -> end.
        RuleDocument right = rule("1", 0, scan(5L), null, List.of(reply("green"), addNumber("correct", 1)), goTo("2"));
        RuleDocument other = rule("2", 1, trigger("SCAN_OTHER"), null, List.of(reply("red"), addNumber("wrong", 1)), STAY);
        RuleDocument idle = rule("3", 2, new TriggerDocument("TIMER_ELAPSED", null, 30), null, List.of(), END);
        RuleDocument win = rule("4", 0, trigger("SCENE_ENTERED"), new RuleCondition("VAR_CMP", "correct", "GTE", "2"),
                List.of(reply("green")), STAY);
        ExperienceDocument document = withVariables(List.of(
                scene("1", true, List.of(), List.of(right, other, idle)),
                scene("2", false, List.of(), List.of(win))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).isEmpty();
    }

    @Test
    void scanWithoutCardYet_isAcceptedAsAGap() {
        RuleDocument rule = rule("1", 0, scan(null), null, List.of(reply("green")), STAY);
        ExperienceDocument document = document(List.of(scene("1", true, List.of(), List.of(rule))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).isEmpty();
    }

    @Test
    void invalidTriggers_areRejected() {
        RuleDocument unknown = rule("1", 0, trigger("TAG_SCANNED"), null, List.of(), STAY);
        RuleDocument timerNoSeconds = rule("2", 1, trigger("TIMER_ELAPSED"), null, List.of(), STAY);
        RuleDocument secondsOnScan = rule("3", 2, new TriggerDocument("SCAN", 5L, 10), null, List.of(), STAY);
        RuleDocument cardOnEnter = rule("4", 3, new TriggerDocument("SCENE_ENTERED", 5L, null), null, List.of(), STAY);
        RuleDocument sessionOnSecondScene = rule("5", 0, trigger("SESSION_STARTED"), null, List.of(), STAY);
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(), List.of(unknown, timerNoSeconds, secondsOnScan, cardOnEnter)),
                scene("2", false, List.of(), List.of(sessionOnSecondScene))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors())
                .anyMatch(e -> e.contains("rule '1' trigger 'TAG_SCANNED' is not valid"))
                .anyMatch(e -> e.contains("rule '2'") && e.contains("needs seconds > 0"))
                .anyMatch(e -> e.contains("rule '3'") && e.contains("can't have seconds"))
                .anyMatch(e -> e.contains("rule '4'") && e.contains("can't have a scan object type"))
                .anyMatch(e -> e.contains("rule '5'") && e.contains("only allowed on the start scene"));
    }

    @Test
    void invalidConditions_areRejected() {
        RuleDocument undeclared = rule("1", 0, trigger("SCENE_ENTERED"), new RuleCondition("VAR_CMP", "score", "GTE", "1"), List.of(), STAY);
        RuleDocument wrongKindAndOp = rule("2", 1, trigger("SCENE_ENTERED"), new RuleCondition("VAR_CMP", "seen", "ABOUT", "x"), List.of(), STAY);
        RuleDocument badFlag = rule("3", 2, trigger("SCENE_ENTERED"), new RuleCondition("FLAG_IS", "seen", null, "maybe"), List.of(), STAY);
        RuleDocument unknownType = rule("4", 3, trigger("SCENE_ENTERED"), new RuleCondition("ALWAYS", null, null, null), List.of(), STAY);
        ExperienceDocument document = withVariables(List.of(
                scene("1", true, List.of(), List.of(undeclared, wrongKindAndOp, badFlag, unknownType))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors())
                .anyMatch(e -> e.contains("rule '1'") && e.contains("'score', which is not declared"))
                .anyMatch(e -> e.contains("rule '2'") && e.contains("needs a NUMBER variable, but 'seen' is a FLAG"))
                .anyMatch(e -> e.contains("rule '2'") && e.contains("op 'ABOUT' is not valid"))
                .anyMatch(e -> e.contains("rule '2'") && e.contains("value 'x' is not an integer"))
                .anyMatch(e -> e.contains("rule '3'") && e.contains("not true or false"))
                .anyMatch(e -> e.contains("rule '4'") && e.contains("condition type 'ALWAYS' is not valid"));
    }

    @Test
    void invalidEffects_areRejected() {
        RuleEffect flagOnNumber = new RuleEffect("SET_FLAG", "correct", "true", null, null, null, null, null, null, null);
        RuleEffect replyNoColor = new RuleEffect("REPLY", null, null, null, null, "Correct!", null, null, null, null);
        RuleEffect addNoAmount = new RuleEffect("ADD_NUMBER", "correct", null, null, null, null, null, null, null, null);
        RuleEffect unknown = new RuleEffect("CONFETTI", null, null, null, null, null, null, null, null, null);
        RuleDocument rule = rule("1", 0, trigger("SCENE_ENTERED"), null, List.of(flagOnNumber, replyNoColor, addNoAmount, unknown), STAY);
        RuleDocument noEffects = rule("2", 1, trigger("SCENE_ENTERED"), null, null, STAY);
        ExperienceDocument document = withVariables(List.of(scene("1", true, List.of(), List.of(rule, noEffects))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors())
                .anyMatch(e -> e.contains("effect 1") && e.contains("needs a FLAG variable, but 'correct' is a NUMBER"))
                .anyMatch(e -> e.contains("effect 2 REPLY needs a color"))
                .anyMatch(e -> e.contains("effect 3 ADD_NUMBER needs an amount"))
                .anyMatch(e -> e.contains("effect 4 type 'CONFETTI' is not valid"))
                .anyMatch(e -> e.contains("rule '2' has no effects list"));
    }

    @Test
    void invalidDestinations_areRejected() {
        RuleDocument stayWithTarget = rule("1", 0, trigger("SCENE_ENTERED"), null, List.of(), new DestinationDocument("STAY", "1"));
        RuleDocument noDestination = rule("2", 1, trigger("SCENE_ENTERED"), null, List.of(), null);
        RuleDocument unknown = rule("3", 2, trigger("SCENE_ENTERED"), null, List.of(), new DestinationDocument("GO_TO_SCENE", null));
        ExperienceDocument document = document(List.of(scene("1", true, List.of(), List.of(stayWithTarget, noDestination, unknown))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors())
                .anyMatch(e -> e.contains("rule '1' destination STAY can't have a target scene"))
                .anyMatch(e -> e.contains("rule '2' has no destination"))
                .anyMatch(e -> e.contains("rule '3' destination 'GO_TO_SCENE' is not valid"));
    }

    @Test
    void scanRuleAfterCatchAll_isRejected() {
        // Listed in the "wrong" order on purpose: position, not list order, decides.
        RuleDocument scanAfter = rule("1", 1, scan(5L), null, List.of(), STAY);
        RuleDocument catchAll = rule("2", 0, trigger("SCAN_OTHER"), null, List.of(), STAY);
        ExperienceDocument document = document(List.of(scene("1", true, List.of(), List.of(scanAfter, catchAll))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("rule '1' can never run") && e.contains("catch-all rule '2'"));
    }

    @Test
    void zeroStartScenes_isRejected() {
        ExperienceDocument document = document(List.of(
                scene("1", false, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("exactly one scene must have is_start"));
    }

    @Test
    void twoStartScenes_isRejected() {
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(), List.of()),
                scene("2", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("exactly one scene must have is_start"));
    }

    @Test
    void newKeyBehindProjectCounter_isRejected() {
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 5, 1, 1, null);
        ExperienceDocument document = document(List.of(
                scene("3", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.errors()).anyMatch(e -> e.contains("scene_key") && e.contains("behind the project's counter"));
    }

    @Test
    void newKeyAtOrAheadOfProjectCounter_isValid() {
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 5, 1, 1, null);
        ExperienceDocument document = document(List.of(
                scene("5", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void existingKeyBehindProjectCounter_isExemptFromFreshnessCheck() {
        // "1" already exists (this write is just updating it), so the counter having moved
        // past it doesn't make it stale - only a genuinely NEW key must be ahead of the counter.
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of("1"), Set.of(), Set.of(), 5, 1, 1, null);
        ExperienceDocument document = document(List.of(
                scene("1", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void multipleProblems_areAllReportedTogether() {
        ExperienceDocument document = document(List.of(
                scene("1", false, List.of(), List.of()),
                scene("1", false, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors())
                .anyMatch(e -> e.contains("used more than once"))
                .anyMatch(e -> e.contains("exactly one scene must have is_start"));
    }

    private SceneDocument scene(String sceneKey, boolean isStart, List<BlockDocument> blocks, List<RuleDocument> rules) {
        return new SceneDocument(sceneKey, "Scene " + sceneKey, 0, isStart, null, blocks, rules);
    }

    private BlockDocument block(String blockKey) {
        return new BlockDocument(blockKey, "TEXT", 0, "{}");
    }

    private RuleDocument rule(String ruleKey, int position, TriggerDocument trigger, RuleCondition condition,
                              List<RuleEffect> effects, DestinationDocument destination) {
        return new RuleDocument(ruleKey, position, trigger, condition, effects, destination);
    }

    private static final DestinationDocument STAY = new DestinationDocument("STAY", null);
    private static final DestinationDocument END = new DestinationDocument("END", null);

    private static DestinationDocument goTo(String sceneKey) {
        return new DestinationDocument("GO_TO", sceneKey);
    }

    private static TriggerDocument trigger(String type) {
        return new TriggerDocument(type, null, null);
    }

    private static TriggerDocument scan(Long scanObjectTypeId) {
        return new TriggerDocument("SCAN", scanObjectTypeId, null);
    }

    private static RuleEffect reply(String color) {
        return new RuleEffect("REPLY", null, null, null, color, "text", null, null, null, null);
    }

    private static RuleEffect addNumber(String variable, int amount) {
        return new RuleEffect("ADD_NUMBER", variable, null, amount, null, null, null, null, null, null);
    }

    // The Quiz's variables, plus a flag.
    private static ExperienceDocument withVariables(List<SceneDocument> scenes) {
        return new ExperienceDocument("DISPLAY", VALID_FLOW, List.of(
                new VariableDocument("correct", "NUMBER", "0"),
                new VariableDocument("wrong", "NUMBER", "0"),
                new VariableDocument("seen", "FLAG", "false")), scenes);
    }
}
