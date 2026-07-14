package com.museotek.box.web.authorization;

import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;

import java.util.List;

public record MeAuthorizationResponse(
        String subject,
        boolean platformAdmin,
        List<String> managedOrganisations,
        List<ProjectMembershipResponse> projectMemberships
) {
    public record ProjectMembershipResponse(String projectId, String orgId, String role) {

        static ProjectMembershipResponse from(CatalogueMeAuthorizationDto.ProjectMembershipView view) {
            return new ProjectMembershipResponse(view.projectId(), view.orgId(), view.role());
        }
    }

    public static MeAuthorizationResponse from(CatalogueMeAuthorizationDto dto) {
        return new MeAuthorizationResponse(
                dto.subject(),
                dto.platformAdmin(),
                dto.managedOrganisations(),
                dto.projectMemberships().stream().map(ProjectMembershipResponse::from).toList()
        );
    }
}
