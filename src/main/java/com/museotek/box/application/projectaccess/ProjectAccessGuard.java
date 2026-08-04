package com.museotek.box.application.projectaccess;

import com.museotek.box.application.project.ProjectCompanionSyncService;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Mandatory entry point for any project-scoped operation, current or future.
 *
 * <p>Validates that the current principal (read implicitly by {@link CatalogueClient}, same
 * as every other Catalogue call in this codebase) can access the given project, and
 * reconciles the local companion row as a side effect: upsert on success, soft-delete on a
 * Catalogue 404 ("lazy-JIT" reconciliation).
 *
 * <p>Any feature whose data lives only in the local DB and is scoped by {@code projectId}
 * (e.g. a future Scene/Rule entity) must call {@link #requireAccess(UUID)} before reading or
 * writing project-scoped local rows — this is what stands between a
 * {@code SceneRepository.findByProjectId(...)} call and a cross-tenant authorization bypass.
 * Catalogue enforces authorization on this call; nothing local does.
 */
@Service
public class ProjectAccessGuard {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public ProjectAccessGuard(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public CatalogueProjectDto requireAccess(UUID projectId) {
        try {
            CatalogueProjectDto project = catalogueClient.getProject(projectId);
            companionSync.upsert(UUID.fromString(project.id()), UUID.fromString(project.orgId()), project.name());
            return project;
        } catch (CatalogueNotFoundException e) {
            companionSync.softDelete(projectId);
            throw e;
        }
    }
}
