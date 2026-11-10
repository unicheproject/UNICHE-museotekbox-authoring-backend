package com.museotek.box.application.screen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Setup codes and screen keys: making them, and the SHA-256 (hex) stored instead of them. A fast hash is enough here
 * because both are random, not chosen by a person.
 */
public final class ScreenSecrets {

    // No 0/O or 1/I, so a code read off a screen or a printout is typed right.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ScreenSecrets() {
    }

    /** 8 characters shown as two groups of 4, e.g. {@code K7QF-M2XR}. */
    public static String newSetupCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH + 1);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** The code as typed, without dashes or spaces and in capitals, so {@code k7qf m2xr} matches {@code K7QF-M2XR}. */
    public static String normaliseSetupCode(String typed) {
        if (typed == null) {
            return "";
        }
        return typed.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** 32 random bytes, URL-safe Base64 without padding (43 characters). */
    public static String newScreenKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
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
