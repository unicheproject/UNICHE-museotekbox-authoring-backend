package com.museotek.box.player.screen;

import com.museotek.box.player.PlayerSocketProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoginLimitsTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
    private final LoginLimits limits = new LoginLimits(clock, properties(3, 2));

    static PlayerSocketProperties properties(int setupFailuresPerHour, int loginsPerMinute) {
        return new PlayerSocketProperties(List.of("http://localhost:5173"), Duration.ofSeconds(10), 5,
                setupFailuresPerHour, Duration.ofMinutes(10), 3, loginsPerMinute, Duration.ofMinutes(1));
    }

    @Test
    void setupPauses_forEveryone_whenTheHourlyFailuresAreReached() {
        limits.recordFailedSetup();
        limits.recordFailedSetup();
        assertThat(limits.setupPaused()).isFalse();

        limits.recordFailedSetup();

        assertThat(limits.setupPaused()).isTrue();
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        assertThat(limits.setupPaused()).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limits.setupPaused()).isFalse();
    }

    @Test
    void failedSetupsOlderThanAnHour_dontCount() {
        limits.recordFailedSetup();
        limits.recordFailedSetup();
        clock.advance(Duration.ofHours(1));

        limits.recordFailedSetup();

        assertThat(limits.setupPaused()).isFalse();
    }

    @Test
    void aScreenLoggingInTooOften_isBlockedForAWhile_otherScreensAreNot() {
        UUID screen = UUID.randomUUID();
        assertThat(limits.allowLogin(screen)).isTrue();
        assertThat(limits.allowLogin(screen)).isTrue();

        assertThat(limits.allowLogin(screen)).isFalse();
        assertThat(limits.allowLogin(UUID.randomUUID())).isTrue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limits.allowLogin(screen)).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limits.allowLogin(screen)).isTrue();
    }

    @Test
    void loginsMoreThanAMinuteApart_dontAddUp() {
        UUID screen = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            assertThat(limits.allowLogin(screen)).isTrue();
            clock.advance(Duration.ofSeconds(31));
        }
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
