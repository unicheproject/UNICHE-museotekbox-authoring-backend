package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RestoreProjectUseCase {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public RestoreProjectUseCase(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public CatalogueProjectDto execute(UUID id) {
        CatalogueProjectDto project = catalogueClient.restoreProject(id);
        companionSync.upsert(id, UUID.fromString(project.orgId()), project.name());
        return project;
    }
}
