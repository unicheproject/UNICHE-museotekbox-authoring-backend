package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Removes a project from a Box. Idempotent: removing a project that isn't assigned is a no-op. */
@Service
public class UnassignProjectFromBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final BoxRepository boxRepository;

    public UnassignProjectFromBoxUseCase(
            OrgAccessGuard orgAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            BoxRepository boxRepository
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.boxRepository = boxRepository;
    }

    @Transactional
    public void execute(UUID orgId, Long boxId, UUID projectId) {
        orgAccessGuard.requireAccess(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        projectAccessGuard.requireAccess(projectId);

        box.getAssignedProjects().removeIf(project -> project.getId().equals(projectId));
    }
}
