package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Projects assigned to a Box, narrowed to what the caller may see. {@code box_projects} is
 * local and knows nothing about roles, so visibility is taken from Catalogue's
 * {@code GET /organisations/{orgId}/projects}, which already answers per caller: managers and
 * platform admins get every live project in the org, members only the projects they belong to.
 * Intersecting with it also drops projects deleted in Catalogue since they were assigned.
 */
@Service
public class ListBoxProjectsQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final CatalogueClient catalogueClient;

    public ListBoxProjectsQuery(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository, CatalogueClient catalogueClient) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.catalogueClient = catalogueClient;
    }

    public List<CatalogueProjectDto> execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireAccess(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        Set<UUID> assignedIds = new HashSet<>(boxRepository.findAssignedProjectIds(box.getId()));
        if (assignedIds.isEmpty()) {
            return List.of();
        }

        List<CatalogueProjectDto> visibleProjects = catalogueClient.listProjectsForOrg(orgId);
        return visibleProjects.stream()
                .filter(project -> assignedIds.contains(UUID.fromString(project.id())))
                .toList();
    }
}
