package com.museotek.box.web.screen;

import com.museotek.box.application.screen.CreateScreenSetupCodeUseCase.CreatedSetupCode;

import java.time.Instant;
import java.util.UUID;

/** The only response that holds the code itself. */
public record CreatedSetupCodeResponse(UUID id, String name, String code, Instant expiresAt) {

    public static CreatedSetupCodeResponse from(CreatedSetupCode created) {
        return new CreatedSetupCodeResponse(created.setupCode().getId(), created.setupCode().getScreenName(),
                created.code(), created.setupCode().getExpiresAt());
    }
}
