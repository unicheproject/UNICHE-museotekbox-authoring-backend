package com.museotek.box.application.player;

import com.museotek.box.application.display.DisplaySessionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Signed media links for displays. A {@code <video>} or {@code <img>} can't send the display token as a header, so
 * the tool gives the display links that carry their own proof: the media, the display session, an expiry and an
 * HMAC over the three. The key is random at every startup (only one copy of the app runs), so a restart ends all
 * links; displays reconnect and get new ones.
 */
@Component
public class DisplayMediaLinks {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    /** A link's parts, as they come back in the media request. */
    public record SignedLink(UUID mediaId, UUID sessionId, long expires, String signature) {
    }

    private final SecretKeySpec key;
    private final DisplaySessionProperties properties;
    private final Clock clock;

    public DisplayMediaLinks(DisplaySessionProperties properties, Clock clock) {
        byte[] bytes = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        this.key = new SecretKeySpec(bytes, ALGORITHM);
        this.properties = properties;
        this.clock = clock;
    }

    /** The path of a media file for this display, relative to the API root, valid for {@code media-link-lifetime}. */
    public String link(UUID sessionId, UUID mediaId) {
        long expires = clock.instant().plus(properties.mediaLinkLifetime()).getEpochSecond();
        return "/display/media/" + mediaId + "/content?session=" + sessionId + "&expires=" + expires
                + "&signature=" + sign(mediaId, sessionId, expires);
    }

    /** True if the tool signed exactly this link and it has not expired. Says nothing about the session itself. */
    public boolean isValid(SignedLink link) {
        if (link.signature() == null || !Instant.ofEpochSecond(link.expires()).isAfter(clock.instant())) {
            return false;
        }
        byte[] expected = sign(link.mediaId(), link.sessionId(), link.expires()).getBytes(StandardCharsets.US_ASCII);
        // Constant time, so the answer's timing gives away nothing about the right signature.
        return MessageDigest.isEqual(expected, link.signature().getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(UUID mediaId, UUID sessionId, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] hmac = mac.doFinal((mediaId + "\n" + sessionId + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Every Java runtime has HmacSHA256", e);
        }
    }
}
