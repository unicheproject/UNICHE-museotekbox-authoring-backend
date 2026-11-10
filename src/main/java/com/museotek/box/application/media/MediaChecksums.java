package com.museotek.box.application.media;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of media files, as lowercase hex. */
final class MediaChecksums {

    private MediaChecksums() {
    }

    /** Wraps a stream so the hash is calculated while it's read; {@link #hex} gives it once the stream is used up. */
    static DigestInputStream sha256While(InputStream content) {
        return new DigestInputStream(content, sha256());
    }

    static String hex(DigestInputStream read) {
        return HexFormat.of().formatHex(read.getMessageDigest().digest());
    }

    static String sha256Of(Resource file) {
        try (DigestInputStream in = sha256While(file.getInputStream())) {
            in.transferTo(OutputStream.nullOutputStream());
            return hex(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read media file " + file.getDescription(), e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
