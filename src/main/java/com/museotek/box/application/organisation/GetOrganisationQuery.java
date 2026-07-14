package com.museotek.box.application.organisation;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueOrganisationDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetOrganisationQuery {

    private final CatalogueClient catalogueClient;

    public GetOrganisationQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public CatalogueOrganisationDto execute(UUID orgId) {
        return catalogueClient.getOrganisation(orgId);
    }
}
