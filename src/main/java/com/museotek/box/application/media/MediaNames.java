package com.museotek.box.application.media;

import com.museotek.box.domain.media.InvalidMediaNameException;

/** The rules for a name someone types for a media item (on upload or rename). */
final class MediaNames {

    static final int MAX_LENGTH = 255;

    private MediaNames() {
    }

    // Trimmed; refused if blank or longer than the column. Unlike a filename, it's kept as typed
    // (slashes included): it's only a label, never used as a path.
    static String requireValid(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidMediaNameException("The name can't be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidMediaNameException("The name may be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
