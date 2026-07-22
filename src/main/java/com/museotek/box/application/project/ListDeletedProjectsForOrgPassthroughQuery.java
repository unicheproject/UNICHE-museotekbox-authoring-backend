package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListDeletedProjectsForOrgPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public ListDeletedProjectsForOrgPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<CatalogueProjectDto> execute(UUID orgId) {
        return catalogueClient.listDeletedProjectsForOrg(orgId);
    }
}
