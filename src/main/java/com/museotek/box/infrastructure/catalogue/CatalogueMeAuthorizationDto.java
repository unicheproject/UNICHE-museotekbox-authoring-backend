package com.museotek.box.infrastructure.catalogue;

import java.util.List;

public record CatalogueMeAuthorizationDto(
        String subject,
        boolean platformAdmin,
        List<String> managedOrganisations,
        List<ProjectMembershipView> projectMemberships,
        String etag,
        long ttlSeconds
) {
    public record ProjectMembershipView(String projectId, String orgId, String role) {}
}
