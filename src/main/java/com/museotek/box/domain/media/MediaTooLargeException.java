package com.museotek.box.domain.media;

/** The upload is bigger than its kind allows. Maps to 413 in GlobalExceptionHandler. */
public class MediaTooLargeException extends RuntimeException {
    public MediaTooLargeException(String message) {
        super(message);
    }
}
