package com.museotek.box.application.media;

/**
 * Checks a file's first bytes against the type it claims, so a renamed file (a script called
 * "photo.jpg", a document sent as "video/mp4") can't pass as media. Only the types the media
 * library allows are known; anything else doesn't match.
 */
final class MediaFileSignatures {

    /** Enough leading bytes for every signature below. */
    static final int HEADER_BYTES = 12;

    private MediaFileSignatures() {
    }

    static boolean matches(String contentType, byte[] header) {
        if (contentType == null || header == null) {
            return false;
        }
        return switch (contentType) {
            case "image/png" -> startsWith(header, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(header, 0, 0xFF, 0xD8, 0xFF);
            case "image/webp" -> startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'E', 'B', 'P');
            case "audio/wav" -> startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'A', 'V', 'E');
            case "audio/ogg" -> startsWith(header, 0, 'O', 'g', 'g', 'S');
            // An ID3 tag, or straight into an MPEG audio frame (11 sync bits set).
            case "audio/mpeg" -> startsWith(header, 0, 'I', 'D', '3')
                    || (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
            // ISO base media: a box size, then "ftyp".
            case "video/mp4" -> startsWith(header, 4, 'f', 't', 'y', 'p');
            default -> false;
        };
    }

    private static boolean startsWith(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
