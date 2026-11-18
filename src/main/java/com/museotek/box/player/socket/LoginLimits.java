package com.museotek.box.player.socket;

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

/**
 * How often one device logs in, against a reconnect loop. Keyed by the device (a display session's id, a Box's id),
 * never by IP address, because a museum's devices usually share one. No limit on wrong tokens: they are 256 random
 * bits, so guessing one is hopeless. In memory: only one copy of the app is active.
 */
@Component
class LoginLimits {

    private static final Logger log = LoggerFactory.getLogger(LoginLimits.class);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final Clock clock;
    private final PlayerSocketProperties properties;

    private final Map<Object, Deque<Instant>> loginsByDevice = new HashMap<>();
    private final Map<Object, Instant> blockedUntil = new HashMap<>();

    LoginLimits(Clock clock, PlayerSocketProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** Counts a login of this device. False if it logs in too often and is (now) blocked for a while. */
    synchronized boolean allowLogin(Object deviceKey) {
        Instant now = clock.instant();
        Instant blocked = blockedUntil.get(deviceKey);
        if (blocked != null) {
            if (now.isBefore(blocked)) {
                return false;
            }
            blockedUntil.remove(deviceKey);
        }
        Deque<Instant> logins = loginsByDevice.computeIfAbsent(deviceKey, key -> new ArrayDeque<>());
        while (!logins.isEmpty() && !logins.peekFirst().isAfter(now.minus(MINUTE))) {
            logins.removeFirst();
        }
        logins.addLast(now);
        if (logins.size() > properties.loginsPerMinute()) {
            blockedUntil.put(deviceKey, now.plus(properties.loginBlock()));
            loginsByDevice.remove(deviceKey);
            log.warn("Device {} logged in more than {} times within a minute: blocked until {}",
                    deviceKey, properties.loginsPerMinute(), now.plus(properties.loginBlock()));
            return false;
        }
        return true;
    }
}
