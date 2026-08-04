package com.museotek.box.application.project;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin delegate to {@link ProjectAccessGuard#requireAccess(UUID)} — kept as its own class so
 * GET /api/v1/projects/{id} still has a 1:1-named use case, matching every other endpoint in
 * this codebase (see GetOrganisationQuery, GetMyAuthorizationQuery). The reconciliation logic
 * itself now lives in ProjectAccessGuard, shared by any other project-scoped feature.
 */
@Service
public class GetProjectQuery {

    private final ProjectAccessGuard projectAccessGuard;

    public GetProjectQuery(ProjectAccessGuard projectAccessGuard) {
        this.projectAccessGuard = projectAccessGuard;
    }

    public CatalogueProjectDto execute(UUID id) {
        return projectAccessGuard.requireAccess(id);
    }
}
