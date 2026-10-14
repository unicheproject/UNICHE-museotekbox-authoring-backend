package com.museotek.box.application.box;

import com.museotek.box.application.experience.ExperienceGaps;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * A published experience (assigned to at least one Box) must stay free of gaps: a Box plays the
 * latest save, so a save that leaves a gap would put a broken experience on it. The author has to
 * fix the gaps in the same save, or unpublish first. Runs after the save is applied, inside its
 * transaction, so a refused save rolls back completely.
 *
 * <p>Temporary: once publishing fixes a version that the Box downloads (device API), drafts can't
 * reach a Box and this check can go.
 */
@Service
public class PublishedExperienceCheck {

    private final BoxRepository boxRepository;
    private final ExperienceGaps experienceGaps;

    public PublishedExperienceCheck(BoxRepository boxRepository, ExperienceGaps experienceGaps) {
        this.boxRepository = boxRepository;
        this.experienceGaps = experienceGaps;
    }

    public void requireNoGapsIfPublished(UUID projectId) {
        List<String> boxes = boxRepository.findNamesByAssignedProjectId(projectId);
        if (boxes.isEmpty()) {
            return;
        }
        List<String> gaps = experienceGaps.find(projectId);
        if (!gaps.isEmpty()) {
            throw new ExperienceNotReadyException("The experience is published on box " + String.join(", ", boxes)
                    + ", so it can't be saved with gaps: fix them, or unpublish it first", gaps);
        }
    }
}
