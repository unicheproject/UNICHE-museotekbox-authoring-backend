package com.museotek.box.application.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Secrets that devices log in with, and the SHA-256 (hex) stored instead of them. A fast hash is enough because the
 * secrets are random, not chosen by a person.
 * <ul>
 * <li>Display tokens: long, never typed (the display page keeps them in the browser).</li>
 * <li>Box keys: short enough to type by hand, from an alphabet without lookalikes.</li>
 * </ul>
 */
public final class SecretTokens {

    private static final int TOKEN_BYTES = 32;
    /** Crockford's base32: digits and capitals without I, L, O and U. 16 of them = 80 random bits. */
    private static final String BOX_KEY_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int BOX_KEY_LENGTH = 16;
    private static final int BOX_KEY_GROUP = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {
    }

    /** 32 random bytes, URL-safe Base64 without padding (43 characters). */
    public static String newToken() {
        byte[] token = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /** A Box key as shown to the manager, in groups of four: {@code K7QM-3XRD-9FZT-2HWB}. */
    public static String newBoxKey() {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < BOX_KEY_LENGTH; i++) {
            if (i > 0 && i % BOX_KEY_GROUP == 0) {
                key.append('-');
            }
            key.append(BOX_KEY_ALPHABET.charAt(RANDOM.nextInt(BOX_KEY_ALPHABET.length())));
        }
        return key.toString();
    }

    /**
     * The hash stored for a Box key, also for a key as a person typed it: dashes and spaces are dropped, case doesn't
     * matter, and the lookalikes I, L and O count as 1, 1 and 0.
     */
    public static String boxKeyHash(String typedKey) {
        String key = typedKey.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT)
                .replace('I', '1').replace('L', '1').replace('O', '0');
        return sha256Hex(key);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
