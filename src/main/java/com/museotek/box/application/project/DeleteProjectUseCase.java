package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteProjectUseCase {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public DeleteProjectUseCase(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public void execute(UUID id) {
        catalogueClient.deleteProject(id);
        companionSync.softDelete(id);
    }
}
