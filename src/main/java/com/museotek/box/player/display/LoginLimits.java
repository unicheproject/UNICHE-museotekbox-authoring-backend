package com.museotek.box.player.display;

import com.museotek.box.player.PlayerSocketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How often one display logs in, against a reconnect loop. Never per IP address, because a museum's devices usually
 * share one. No limit on wrong tokens: they are 256 random bits, so guessing one is hopeless. In memory: only one
 * copy of the app is active.
 */
@Component
class LoginLimits {

    private static final Logger log = LoggerFactory.getLogger(LoginLimits.class);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final Clock clock;
    private final PlayerSocketProperties properties;

    private final Map<UUID, Deque<Instant>> loginsBySession = new HashMap<>();
    private final Map<UUID, Instant> blockedUntil = new HashMap<>();

    LoginLimits(Clock clock, PlayerSocketProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** Counts a login of this display. False if it logs in too often and is (now) blocked for a while. */
    synchronized boolean allowLogin(UUID sessionId) {
        Instant now = clock.instant();
        Instant blocked = blockedUntil.get(sessionId);
        if (blocked != null) {
            if (now.isBefore(blocked)) {
                return false;
            }
            blockedUntil.remove(sessionId);
        }
        Deque<Instant> logins = loginsBySession.computeIfAbsent(sessionId, id -> new ArrayDeque<>());
        while (!logins.isEmpty() && !logins.peekFirst().isAfter(now.minus(MINUTE))) {
            logins.removeFirst();
        }
        logins.addLast(now);
        if (logins.size() > properties.loginsPerMinute()) {
            blockedUntil.put(sessionId, now.plus(properties.loginBlock()));
            loginsBySession.remove(sessionId);
            log.warn("Display {} logged in more than {} times within a minute: blocked until {}",
                    sessionId, properties.loginsPerMinute(), now.plus(properties.loginBlock()));
            return false;
        }
        return true;
    }
}
