package com.museotek.box.web.organisation;

import com.museotek.box.application.organisation.OrgMemberView;

import java.time.Instant;

public record OrgMemberResponse(
        String userId,
        String email,
        String displayName,
        String role,
        Instant since
) {

    public static OrgMemberResponse from(OrgMemberView view) {
        return new OrgMemberResponse(view.userId(), view.email(), view.displayName(), view.role(), view.since());
    }
}
