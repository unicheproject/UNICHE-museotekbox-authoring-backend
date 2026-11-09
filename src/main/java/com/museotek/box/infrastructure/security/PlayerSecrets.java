package com.museotek.box.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * The shared secrets the player project logs in with on {@code /player/v1}: one, or two while one is being
 * replaced. Only their SHA-256 hashes are kept in memory.
 */
public final class PlayerSecrets {

    static final int MIN_LENGTH = 32;
    static final int MAX_COUNT = 2;

    private final List<byte[]> hashes;

    /** Fails on a missing, too short or extra secret, so a misconfigured deployment doesn't start. */
    public PlayerSecrets(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            throw new IllegalStateException(
                    "museotek.player.secrets is not configured. Set the PLAYER_SECRETS environment variable to the "
                            + "secret the player project sends (two, comma-separated, while one is being replaced).");
        }
        List<String> secrets = Arrays.stream(commaSeparated.split(",", -1)).map(String::trim).toList();
        if (secrets.size() > MAX_COUNT) {
            throw new IllegalStateException(
                    "PLAYER_SECRETS holds %d secrets; at most %d are accepted (the current one and the one replacing it)."
                            .formatted(secrets.size(), MAX_COUNT));
        }
        for (String secret : secrets) {
            if (secret.length() < MIN_LENGTH) {
                // Never put the secret itself in the message: it ends up in the startup log.
                throw new IllegalStateException(
                        "Every secret in PLAYER_SECRETS must be at least %d characters long.".formatted(MIN_LENGTH));
            }
        }
        this.hashes = secrets.stream().map(PlayerSecrets::sha256).toList();
    }

    /**
     * Whether the candidate is one of the secrets. Comparing fixed-length hashes with {@link MessageDigest#isEqual}
     * and always checking every secret keeps the time the same however close a guess is.
     */
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        byte[] candidateHash = sha256(candidate);
        boolean matched = false;
        for (byte[] hash : hashes) {
            matched |= MessageDigest.isEqual(hash, candidateHash);
        }
        return matched;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
