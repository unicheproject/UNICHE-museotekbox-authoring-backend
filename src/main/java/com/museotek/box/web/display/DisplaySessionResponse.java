package com.museotek.box.web.display;

import com.museotek.box.domain.display.DisplaySession;

import java.time.Instant;
import java.util.UUID;

/** A display of a Box, without its token. */
public record DisplaySessionResponse(UUID id, Instant createdAt, Instant lastSeenAt, Instant expiresAt) {

    public static DisplaySessionResponse from(DisplaySession session) {
        return new DisplaySessionResponse(session.getId(), session.getCreatedAt(), session.getLastSeenAt(),
                session.getExpiresAt());
    }
}
