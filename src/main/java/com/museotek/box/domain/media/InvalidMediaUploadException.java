package com.museotek.box.domain.media;

/** No usable file in the upload (missing or empty). Maps to 400 in GlobalExceptionHandler. */
public class InvalidMediaUploadException extends RuntimeException {
    public InvalidMediaUploadException(String message) {
        super(message);
    }
}
