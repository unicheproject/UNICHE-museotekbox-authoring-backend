package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueInvitationSummaryDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * There is no dedicated Catalogue endpoint for "this project's current members" — a Curator
 * membership is only ever visible via the project's invitation history. An ACCEPTED invitation
 * is exactly the event that creates a membership (see Catalogue's InvitationService.accept), so
 * filtering to ACCEPTED here reconstructs the member list without any Catalogue-side change.
 * Manager-of-org or admin only, since that's the access Catalogue's invitations endpoint requires.
 */
@Service
public class ListProjectMembersPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public ListProjectMembersPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<CatalogueInvitationSummaryDto> execute(UUID projectId) {
        return catalogueClient.listInvitationsForProject(projectId).stream()
                .filter(invitation -> "ACCEPTED".equals(invitation.status()))
                .toList();
    }
}
