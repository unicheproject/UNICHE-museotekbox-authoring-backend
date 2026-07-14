package com.museotek.box.web.project;

import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;

public record ProjectResponse(String id, String orgId, String name, String slug, String status, String toolSlug) {

    public static ProjectResponse from(CatalogueProjectDto dto) {
        return new ProjectResponse(dto.id(), dto.orgId(), dto.name(), dto.slug(), dto.status(), dto.tool().slug());
    }
}
