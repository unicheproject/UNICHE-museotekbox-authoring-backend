package com.museotek.box.web.project;

import com.museotek.box.infrastructure.catalogue.CatalogueInvitationSummaryDto;

import java.time.Instant;

/**
 * Sourced from an accepted invitation, not a membership row directly (Catalogue has no bulk
 * membership-listing endpoint) — so there's an email + role but no userId, and {@code invitedAt}
 * is when the invitation was issued, not the exact moment it was accepted.
 */
public record ProjectMemberResponse(
        String email,
        String role,
        Instant invitedAt
) {

    public static ProjectMemberResponse from(CatalogueInvitationSummaryDto dto) {
        return new ProjectMemberResponse(dto.email(), dto.role(), dto.createdAt());
    }
}
