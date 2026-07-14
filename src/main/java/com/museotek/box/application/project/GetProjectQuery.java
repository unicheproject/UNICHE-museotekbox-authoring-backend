package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetProjectQuery {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public GetProjectQuery(CatalogueClient catalogueClient, ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public CatalogueProjectDto execute(UUID id) {
        try {
            CatalogueProjectDto project = catalogueClient.getProject(id);
            companionSync.upsert(UUID.fromString(project.id()), UUID.fromString(project.orgId()), project.name());
            return project;
        } catch (CatalogueNotFoundException e) {
            // "lazy-JIT" deletion reconciliation: Catalogue no longer knows this project,
            // so clean up the local companion row too before letting the 404 propagate.
            companionSync.softDelete(id);
            throw e;
        }
    }
}
