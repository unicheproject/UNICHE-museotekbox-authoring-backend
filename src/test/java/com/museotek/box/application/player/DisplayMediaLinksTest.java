package com.museotek.box.application.player;

import com.museotek.box.application.display.DisplaySessionProperties;
import com.museotek.box.application.player.DisplayMediaLinks.SignedLink;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayMediaLinksTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final DisplaySessionProperties PROPERTIES =
            new DisplaySessionProperties(Duration.ofDays(30), Duration.ofHours(1), Duration.ofHours(12));

    private final UUID mediaId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final DisplayMediaLinks links = links(NOW);

    private static DisplayMediaLinks links(Instant now) {
        return new DisplayMediaLinks(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Reads a link back the way the media request receives it. */
    private static SignedLink parse(String link) {
        UriComponents uri = UriComponentsBuilder.fromUriString(link).build();
        return new SignedLink(UUID.fromString(uri.getPathSegments().get(2)),
                UUID.fromString(uri.getQueryParams().getFirst("session")),
                Long.parseLong(uri.getQueryParams().getFirst("expires")),
                uri.getQueryParams().getFirst("signature"));
    }

    @Test
    void aLinkItMade_isValid_untilItExpires() {
        String link = links.link(sessionId, mediaId);

        assertThat(link).startsWith("/display/media/" + mediaId + "/content?session=" + sessionId + "&expires=");
        SignedLink parsed = parse(link);
        assertThat(parsed.expires()).isEqualTo(NOW.plus(Duration.ofHours(12)).getEpochSecond());
        assertThat(links.isValid(parsed)).isTrue();
    }

    @Test
    void aLink_stopsWorkingWhenItExpires() {
        Instant[] now = {NOW};
        Clock moving = new Clock() {
            @Override
            public Instant instant() {
                return now[0];
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }
        };
        DisplayMediaLinks sameKey = new DisplayMediaLinks(PROPERTIES, moving);
        SignedLink link = parse(sameKey.link(sessionId, mediaId));

        now[0] = NOW.plus(Duration.ofHours(12)).minusSeconds(1);
        assertThat(sameKey.isValid(link)).isTrue();
        now[0] = NOW.plus(Duration.ofHours(12));
        assertThat(sameKey.isValid(link)).isFalse();
    }

    @Test
    void changingAnyPart_breaksTheSignature() {
        SignedLink link = parse(links.link(sessionId, mediaId));

        assertThat(links.isValid(new SignedLink(UUID.randomUUID(), link.sessionId(), link.expires(), link.signature())))
                .isFalse();
        assertThat(links.isValid(new SignedLink(link.mediaId(), UUID.randomUUID(), link.expires(), link.signature())))
                .isFalse();
        assertThat(links.isValid(new SignedLink(link.mediaId(), link.sessionId(), link.expires() + 3600, link.signature())))
                .isFalse();
        assertThat(links.isValid(new SignedLink(link.mediaId(), link.sessionId(), link.expires(), "x" + link.signature())))
                .isFalse();
        assertThat(links.isValid(new SignedLink(link.mediaId(), link.sessionId(), link.expires(), null))).isFalse();
    }

    @Test
    void aRestart_endsEveryLink() {
        SignedLink link = parse(links.link(sessionId, mediaId));

        assertThat(links(NOW).isValid(link)).isFalse();
    }
}
