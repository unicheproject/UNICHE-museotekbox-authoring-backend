package com.museotek.box.application.experience;

import java.util.Set;

/**
 * Everything the validator needs to know about a project's current state to judge an
 * incoming {@link ExperienceDocument}, without touching a repository itself: which keys
 * already exist (so a key can be told apart as an update vs. a new row) and the project's
 * three running counters (so a new key's freshness can be checked). {@code existingOutput} is
 * the output stored by an earlier save, or {@code null} if the experience was never saved.
 */
public record ExperienceWriteContext(
        Set<String> existingSceneKeys,
        Set<String> existingBlockKeys,
        Set<String> existingRuleKeys,
        int nextSceneSeq,
        int nextBlockSeq,
        int nextRuleSeq,
        String existingOutput
) {
}
