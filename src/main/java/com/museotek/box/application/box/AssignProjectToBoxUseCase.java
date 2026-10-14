package com.museotek.box.application.box;

import com.museotek.box.application.experience.ExperienceGaps;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.domain.box.ProjectNotInBoxOrgException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Makes a project available on a Box: publishing. Org managers only. Idempotent: assigning an
 * already-assigned project is a no-op. The experience must have no gaps ({@link ExperienceGaps}),
 * and its start card must not already start another experience on the Box, since that card is
 * how a visitor picks it ({@link BoxStartCards}). The Box's current experience is not touched:
 * the Box sets it itself when a start card is placed.
 */
@Service
public class AssignProjectToBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final BoxRepository boxRepository;
    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final ExperienceGaps experienceGaps;
    private final BoxStartCards boxStartCards;

    public AssignProjectToBoxUseCase(
            OrgAccessGuard orgAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            BoxRepository boxRepository,
            ProjectRepository projectRepository,
            SceneRepository sceneRepository,
            ExperienceGaps experienceGaps,
            BoxStartCards boxStartCards
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.boxRepository = boxRepository;
        this.projectRepository = projectRepository;
        this.sceneRepository = sceneRepository;
        this.experienceGaps = experienceGaps;
        this.boxStartCards = boxStartCards;
    }

    @Transactional
    public void execute(UUID orgId, Long boxId, UUID projectId) {
        orgAccessGuard.requireManager(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        // Also upserts the local companion row, which box_projects' FK needs.
        CatalogueProjectDto project = projectAccessGuard.requireAccess(projectId);
        if (!orgId.toString().equals(project.orgId())) {
            throw new ProjectNotInBoxOrgException("Project " + projectId + " does not belong to org " + orgId);
        }

        if (boxRepository.findAssignedProjectIds(boxId).contains(projectId)) {
            return;
        }

        List<String> gaps = experienceGaps.find(projectId);
        if (!gaps.isEmpty()) {
            throw new ExperienceNotReadyException("Experience " + projectId + " has gaps and can't be assigned to a box yet", gaps);
        }
        // No gaps means the start scene exists and has a start card.
        Long startCardTypeId = sceneRepository.findByProjectIdAndStartTrue(projectId).orElseThrow()
                .getInitCardType().getId();
        boxStartCards.requireFreeOnBox(boxId, projectId, startCardTypeId);

        box.getAssignedProjects().add(projectRepository.getReferenceById(projectId));
    }
}
