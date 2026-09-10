package com.museotek.box.infrastructure.catalogue;

import java.time.Instant;

/** Mirrors Catalogue's InvitationSummaryResponse (GET /projects/{projectId}/invitations). */
public record CatalogueInvitationSummaryDto(
        String id,
        String projectId,
        String projectName,
        String orgId,
        String orgName,
        String email,
        String role,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
}
