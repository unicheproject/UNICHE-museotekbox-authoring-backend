package com.museotek.box.application.authorization;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
import org.springframework.stereotype.Service;

@Service
public class GetMyAuthorizationQuery {

    private final CatalogueClient catalogueClient;

    public GetMyAuthorizationQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public CatalogueMeAuthorizationDto execute() {
        return catalogueClient.getMeAuthorization();
    }
}
