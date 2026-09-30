package com.museotek.box.infrastructure.catalogue;

import java.time.Instant;

/**
 * A project as Catalogue returns it: the platform's view of the project as an item (name, slug,
 * status, dates). Same id as our local {@code Project} companion row, but a different set of
 * fields. See README, "Project vs Experience".
 */
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
