package com.museotek.box.domain.media;

/** No media item with this id in this org, or its file is gone. Maps to 404 in GlobalExceptionHandler. */
public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String message) {
        super(message);
    }
}
