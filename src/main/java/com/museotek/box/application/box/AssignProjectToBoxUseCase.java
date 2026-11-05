package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.ProjectNotInBoxOrgException;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.domain.publish.UnpublishedExperienceNotAssignableException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Chooses a Box for an experience: the Box then plays its latest published copy. Org managers only.
 * Idempotent: assigning an already-assigned project is a no-op. The experience must have been
 * published (the copy was checked for gaps then), and the copy's start card must not already start
 * another experience on the Box, since that card is how a visitor picks it ({@link BoxStartCards}).
 * The Box's current experience is not touched: the Box sets it itself when a start card is placed.
 */
@Service
public class AssignProjectToBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final BoxRepository boxRepository;
    private final ProjectRepository projectRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final BoxStartCards boxStartCards;

    public AssignProjectToBoxUseCase(
            OrgAccessGuard orgAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            BoxRepository boxRepository,
            ProjectRepository projectRepository,
            PublishedExperienceRepository publishedExperienceRepository,
            BoxStartCards boxStartCards
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.boxRepository = boxRepository;
        this.projectRepository = projectRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
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

        PublishedExperience latest = publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)
                .orElseThrow(() -> new UnpublishedExperienceNotAssignableException(
                        "Experience " + projectId + " has never been published, so it can't be assigned to a box yet"));
        boxStartCards.requireFreeOnBox(boxId, projectId, latest.getStartCardTypeId());

        box.getAssignedProjects().add(projectRepository.getReferenceById(projectId));
    }
}
