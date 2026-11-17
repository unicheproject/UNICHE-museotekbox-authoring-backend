package com.museotek.box.web.display;

import com.museotek.box.application.display.CreateDisplaySessionUseCase.CreatedDisplaySession;

import java.time.Instant;
import java.util.UUID;

/** The new display, with its token: the only time the token is ever sent. */
public record CreatedDisplaySessionResponse(UUID id, String token, Instant createdAt, Instant expiresAt) {

    public static CreatedDisplaySessionResponse from(CreatedDisplaySession created) {
        return new CreatedDisplaySessionResponse(created.session().getId(), created.token(),
                created.session().getCreatedAt(), created.session().getExpiresAt());
    }
}
