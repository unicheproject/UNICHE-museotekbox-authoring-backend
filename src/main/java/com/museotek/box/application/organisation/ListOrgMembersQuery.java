package com.museotek.box.application.organisation;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates everyone associated with an org from two existing Catalogue endpoints, since
 * Catalogue has no single endpoint for this: managers (GET .../managers) plus anyone with an
 * ACCEPTED invitation on one of the org's projects (GET /projects/{id}/invitations, called once
 * per project). A user who is both is reported once, as MANAGER — the broader grant, and the one
 * we have a userId/displayName for.
 */
@Service
public class ListOrgMembersQuery {

    private final CatalogueClient catalogueClient;

    public ListOrgMembersQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<OrgMemberView> execute(UUID orgId) {
        Map<String, OrgMemberView> byEmail = new LinkedHashMap<>();

        for (var manager : catalogueClient.listOrgManagers(orgId)) {
            byEmail.put(manager.email(),
                    new OrgMemberView(manager.userId(), manager.email(), manager.displayName(), "MANAGER", manager.grantedAt()));
        }

        for (CatalogueProjectDto project : catalogueClient.listProjectsForOrg(orgId)) {
            for (var invitation : catalogueClient.listInvitationsForProject(UUID.fromString(project.id()))) {
                if (!"ACCEPTED".equals(invitation.status())) {
                    continue;
                }
                // A manager already covers every project as Curator — don't downgrade their row.
                byEmail.computeIfAbsent(invitation.email(),
                        email -> new OrgMemberView(null, email, null, "CURATOR", invitation.createdAt()));
            }
        }

        return List.copyOf(byEmail.values());
    }
}
