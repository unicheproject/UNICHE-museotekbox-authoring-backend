package com.museotek.box.application.organisation;

import java.time.Instant;

/**
 * A user associated with an organisation, synthesized from two separate Catalogue reads (org
 * managers + accepted invitations across the org's projects) — not a single Catalogue DTO.
 * {@code userId}/{@code displayName} are null for a CURATOR row: an accepted invitation carries
 * only the invitee's email, since Catalogue has no bulk membership-listing endpoint to source a
 * userId from.
 */
public record OrgMemberView(
        String userId,
        String email,
        String displayName,
        String role,
        Instant since
) {
}
