package com.museotek.box.infrastructure.catalogue;

import java.time.Instant;

public record CatalogueProjectDto(
        String id,
        String orgId,
        String name,
        String slug,
        String status,
        CatalogueToolDto tool,
        Instant createdAt,
        Instant updatedAt
) {
    public record CatalogueToolDto(String slug) {}
}
