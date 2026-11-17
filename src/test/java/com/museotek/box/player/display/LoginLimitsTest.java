package com.museotek.box.player.display;

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
    private final LoginLimits limits = new LoginLimits(clock, properties(2));

    static PlayerSocketProperties properties(int loginsPerMinute) {
        return new PlayerSocketProperties(List.of("http://localhost:5173"), Duration.ofSeconds(5), loginsPerMinute,
                Duration.ofMinutes(1));
    }

    @Test
    void aDisplayLoggingInTooOften_isBlockedForAWhile_otherDisplaysAreNot() {
        UUID display = UUID.randomUUID();
        assertThat(limits.allowLogin(display)).isTrue();
        assertThat(limits.allowLogin(display)).isTrue();

        assertThat(limits.allowLogin(display)).isFalse();
        assertThat(limits.allowLogin(UUID.randomUUID())).isTrue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limits.allowLogin(display)).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limits.allowLogin(display)).isTrue();
    }

    @Test
    void loginsMoreThanAMinuteApart_dontAddUp() {
        UUID display = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            assertThat(limits.allowLogin(display)).isTrue();
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
