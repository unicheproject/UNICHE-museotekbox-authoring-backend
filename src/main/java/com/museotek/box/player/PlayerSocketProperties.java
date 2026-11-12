package com.museotek.box.player;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Settings of the player's websockets ({@code museotek.player.socket.*}). Defaults live in application.properties
 * only; a missing or non-positive value stops the app at startup.
 */
@Validated
@ConfigurationProperties("museotek.player.socket")
public record PlayerSocketProperties(
        @NotEmpty List<String> allowedOrigins,
        @NotNull Duration loginTimeout,
        @Positive int setupFailuresPerConnection,
        @Positive int setupFailuresPerHour,
        @NotNull Duration setupPause,
        @Positive int keyFailuresPerConnection,
        @Positive int loginsPerMinute,
        @NotNull Duration loginBlock
) {
}
