package com.museotek.box.infrastructure.catalogue;

public record CatalogueProjectDto(
        String id,
        String orgId,
        String name,
        String slug,
        String status,
        CatalogueToolDto tool
) {
    public record CatalogueToolDto(String slug) {}
}
