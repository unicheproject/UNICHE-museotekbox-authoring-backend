package com.museotek.box.web.screen;

import com.museotek.box.domain.screen.ScreenSetupCode;

import java.time.Instant;
import java.util.UUID;

public record SetupCodeResponse(UUID id, String name, Instant createdAt, Instant expiresAt) {

    public static SetupCodeResponse from(ScreenSetupCode setupCode) {
        return new SetupCodeResponse(setupCode.getId(), setupCode.getScreenName(), setupCode.getCreatedAt(),
                setupCode.getExpiresAt());
    }
}
