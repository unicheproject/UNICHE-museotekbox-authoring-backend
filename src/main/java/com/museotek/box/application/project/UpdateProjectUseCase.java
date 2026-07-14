package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.catalogue.CatalogueUpdateProjectRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateProjectUseCase {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public UpdateProjectUseCase(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public CatalogueProjectDto execute(UUID id, CatalogueUpdateProjectRequest request) {
        CatalogueProjectDto project = catalogueClient.updateProject(id, request);
        companionSync.upsert(id, UUID.fromString(project.orgId()), project.name());
        return project;
    }
}
