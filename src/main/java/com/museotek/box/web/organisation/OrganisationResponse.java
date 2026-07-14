package com.museotek.box.web.organisation;

import com.museotek.box.infrastructure.catalogue.CatalogueOrganisationDto;

public record OrganisationResponse(String id, String name, String slug, String status) {

    public static OrganisationResponse from(CatalogueOrganisationDto dto) {
        return new OrganisationResponse(dto.id(), dto.name(), dto.slug(), dto.status());
    }
}
