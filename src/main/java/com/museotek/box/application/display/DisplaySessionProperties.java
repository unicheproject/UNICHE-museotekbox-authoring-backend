package com.museotek.box.application.display;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Display sessions ({@code museotek.display.*}). Defaults live in application.properties only; a missing value stops
 * the app at startup.
 *
 * @param sessionLifetime  a display stops working when its token goes unused this long
 * @param lastSeenInterval how often, at most, a used token updates last_seen_at and expires_at
 * @param mediaLinkLifetime how long a signed media link given to a display keeps working
 */
@Validated
@ConfigurationProperties("museotek.display")
public record DisplaySessionProperties(@NotNull Duration sessionLifetime, @NotNull Duration lastSeenInterval,
                                       @NotNull Duration mediaLinkLifetime) {
}
