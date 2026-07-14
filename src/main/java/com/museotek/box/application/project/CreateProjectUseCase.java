package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueCreateProjectRequest;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateProjectUseCase {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public CreateProjectUseCase(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public CatalogueProjectDto execute(UUID orgId, CatalogueCreateProjectRequest request) {
        CatalogueProjectDto project = catalogueClient.createProject(orgId, request);
        // "create-up": materialise the local companion row right after Catalogue confirms creation.
        companionSync.upsert(UUID.fromString(project.id()), orgId, project.name());
        return project;
    }
}
