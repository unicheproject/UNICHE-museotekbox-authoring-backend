package com.museotek.box.web.project;

import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;

import java.time.Instant;

public record ProjectResponse(
        String id,
        String orgId,
        String name,
        String slug,
        String status,
        String toolSlug,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(CatalogueProjectDto dto) {
        return new ProjectResponse(
                dto.id(), dto.orgId(), dto.name(), dto.slug(), dto.status(), dto.tool().slug(),
                dto.createdAt(), dto.updatedAt());
    }
}
