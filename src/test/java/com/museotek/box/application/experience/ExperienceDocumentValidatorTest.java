package com.museotek.box.application.experience;

import com.museotek.box.application.experience.ExperienceDocument.BlockDocument;
import com.museotek.box.application.experience.ExperienceDocument.RuleDocument;
import com.museotek.box.application.experience.ExperienceDocument.SceneDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceDocumentValidatorTest {

    private final ExperienceDocumentValidator validator = new ExperienceDocumentValidator();

    private static final ExperienceWriteContext EMPTY_PROJECT_CONTEXT =
            new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 1, 1, 1);

    @Test
    void wellFormedNewDocument_isValid() {
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(block("1")), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void malformedKey_isRejected() {
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("01", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("scene_key") && e.contains("not well-formed"));
    }

    @Test
    void duplicateSceneKey_isRejected() {
        ExperienceDocument document = new ExperienceDocument(List.of(
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
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(block("1")), List.of()),
                scene("2", false, List.of(block("1")), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("block_key") && e.contains("used more than once"));
    }

    @Test
    void targetSceneKeyNotInDocument_isRejected() {
        RuleDocument rule = rule("1", "GO_TO_SCENE", null, "99", null);
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(), List.of(rule))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("target_scene_key"));
    }

    @Test
    void targetBlockKeyInAnotherScene_isRejected() {
        // target_block_key must resolve within the rule's OWN scene, not anywhere in the project.
        RuleDocument rule = rule("1", "SHOW_BLOCK", null, null, "1");
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(), List.of(rule)),
                scene("2", false, List.of(block("2")), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("target_block_key") && e.contains("same scene"));
    }

    @Test
    void triggerBlockKeyInOwnScene_isValid() {
        BlockDocument block = block("1");
        RuleDocument rule = rule("1", "BLOCK_COMPLETED", "1", null, null);
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(block), List.of(rule))));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void zeroStartScenes_isRejected() {
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", false, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("exactly one scene must have is_start"));
    }

    @Test
    void twoStartScenes_isRejected() {
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(), List.of()),
                scene("2", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, EMPTY_PROJECT_CONTEXT);

        assertThat(result.errors()).anyMatch(e -> e.contains("exactly one scene must have is_start"));
    }

    @Test
    void newKeyBehindProjectCounter_isRejected() {
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 5, 1, 1);
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("3", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.errors()).anyMatch(e -> e.contains("scene_key") && e.contains("behind the project's counter"));
    }

    @Test
    void newKeyAtOrAheadOfProjectCounter_isValid() {
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of(), Set.of(), Set.of(), 5, 1, 1);
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("5", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void existingKeyBehindProjectCounter_isExemptFromFreshnessCheck() {
        // "1" already exists (this write is just updating it), so the counter having moved
        // past it doesn't make it stale - only a genuinely NEW key must be ahead of the counter.
        ExperienceWriteContext context = new ExperienceWriteContext(Set.of("1"), Set.of(), Set.of(), 5, 1, 1);
        ExperienceDocument document = new ExperienceDocument(List.of(
                scene("1", true, List.of(), List.of())));

        ExperienceValidationResult result = validator.validate(document, context);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void multipleProblems_areAllReportedTogether() {
        ExperienceDocument document = new ExperienceDocument(List.of(
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

    private RuleDocument rule(String ruleKey, String action, String triggerBlockKey, String targetSceneKey, String targetBlockKey) {
        return new RuleDocument(ruleKey, "TAG_SCANNED", action, 0, null, triggerBlockKey, targetSceneKey, targetBlockKey);
    }
}
