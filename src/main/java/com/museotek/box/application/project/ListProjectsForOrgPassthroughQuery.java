package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListProjectsForOrgPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public ListProjectsForOrgPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<CatalogueProjectDto> execute(UUID orgId) {
        return catalogueClient.listProjectsForOrg(orgId);
    }
}
