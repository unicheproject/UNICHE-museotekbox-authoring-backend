package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.ProjectNotInBoxOrgException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Makes a project available on a Box. Idempotent: assigning an already-assigned project is a
 * no-op. Only a plain assignment — publish version and the Box's current experience are
 * deliberately not touched here (see docs/proposal-experience-settings-and-publishing.md).
 */
@Service
public class AssignProjectToBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final BoxRepository boxRepository;
    private final ProjectRepository projectRepository;

    public AssignProjectToBoxUseCase(
            OrgAccessGuard orgAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            BoxRepository boxRepository,
            ProjectRepository projectRepository
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.boxRepository = boxRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void execute(UUID orgId, Long boxId, UUID projectId) {
        orgAccessGuard.requireAccess(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        // Also upserts the local companion row, which box_projects' FK needs.
        CatalogueProjectDto project = projectAccessGuard.requireAccess(projectId);
        if (!orgId.toString().equals(project.orgId())) {
            throw new ProjectNotInBoxOrgException("Project " + projectId + " does not belong to org " + orgId);
        }

        box.getAssignedProjects().add(projectRepository.getReferenceById(projectId));
    }
}
