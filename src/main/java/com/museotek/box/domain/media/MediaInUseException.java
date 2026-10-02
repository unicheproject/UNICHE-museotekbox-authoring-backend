package com.museotek.box.domain.media;

/** A media item can't be deleted while an experience still uses it. Maps to 409 in GlobalExceptionHandler. */
public class MediaInUseException extends RuntimeException {
    public MediaInUseException(String message) {
        super(message);
    }
}
