package com.museotek.box.web.screen;

import com.museotek.box.domain.screen.Screen;

import java.time.Instant;
import java.util.UUID;

public record ScreenResponse(UUID id, String name, Instant createdAt, Instant lastSeenAt) {

    public static ScreenResponse from(Screen screen) {
        return new ScreenResponse(screen.getId(), screen.getName(), screen.getCreatedAt(), screen.getLastSeenAt());
    }
}
