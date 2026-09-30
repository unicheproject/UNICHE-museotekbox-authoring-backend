package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Removes a project from a Box. Org managers only. Idempotent: removing a project that isn't
 * assigned is a no-op. No project access check: the manager check on the Box's org already
 * covers it, and skipping it lets a manager clear an assignment whose project was since deleted
 * in Catalogue.
 */
@Service
public class UnassignProjectFromBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;

    public UnassignProjectFromBoxUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
    }

    @Transactional
    public void execute(UUID orgId, Long boxId, UUID projectId) {
        orgAccessGuard.requireManager(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        box.getAssignedProjects().removeIf(project -> project.getId().equals(projectId));
    }
}
