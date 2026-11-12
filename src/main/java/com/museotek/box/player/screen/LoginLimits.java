package com.museotek.box.player.screen;

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
 * The limits that span connections: wrong setup codes of all screens together, and how often one screen logs in.
 * Never per IP address, because a museum's PCs usually share one. In memory: only one copy of the app is active.
 */
@Component
class LoginLimits {

    private static final Logger log = LoggerFactory.getLogger(LoginLimits.class);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final Clock clock;
    private final PlayerSocketProperties properties;

    private final Deque<Instant> failedSetups = new ArrayDeque<>();
    private Instant setupPausedUntil = Instant.MIN;
    private final Map<UUID, Deque<Instant>> loginsByScreen = new HashMap<>();
    private final Map<UUID, Instant> blockedUntil = new HashMap<>();

    LoginLimits(Clock clock, PlayerSocketProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    synchronized boolean setupPaused() {
        return clock.instant().isBefore(setupPausedUntil);
    }

    synchronized void recordFailedSetup() {
        Instant now = clock.instant();
        dropOlderThan(failedSetups, now.minus(HOUR));
        failedSetups.addLast(now);
        if (failedSetups.size() >= properties.setupFailuresPerHour()) {
            setupPausedUntil = now.plus(properties.setupPause());
            failedSetups.clear();
            log.warn("{} wrong setup codes within an hour: setup codes are refused until {}",
                    properties.setupFailuresPerHour(), setupPausedUntil);
        }
    }

    /** Counts a login of this screen. False if the screen logs in too often and is (now) blocked for a while. */
    synchronized boolean allowLogin(UUID screenId) {
        Instant now = clock.instant();
        Instant blocked = blockedUntil.get(screenId);
        if (blocked != null) {
            if (now.isBefore(blocked)) {
                return false;
            }
            blockedUntil.remove(screenId);
        }
        Deque<Instant> logins = loginsByScreen.computeIfAbsent(screenId, id -> new ArrayDeque<>());
        dropOlderThan(logins, now.minus(MINUTE));
        logins.addLast(now);
        if (logins.size() > properties.loginsPerMinute()) {
            blockedUntil.put(screenId, now.plus(properties.loginBlock()));
            loginsByScreen.remove(screenId);
            log.warn("Screen {} logged in more than {} times within a minute: blocked until {}",
                    screenId, properties.loginsPerMinute(), now.plus(properties.loginBlock()));
            return false;
        }
        return true;
    }

    private static void dropOlderThan(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.removeFirst();
        }
    }
}
