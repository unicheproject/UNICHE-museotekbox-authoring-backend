package com.museotek.box.infrastructure.catalogue;

import java.time.Instant;

/** Mirrors Catalogue's ManagerResponse (GET /organisations/{orgId}/managers). */
public record CatalogueManagerDto(
        String userId,
        String email,
        String displayName,
        Instant grantedAt
) {
}
