package com.museotek.box.domain.media;

/** The upload's type isn't allowed, or its content doesn't match the type it claims. Maps to 415 in GlobalExceptionHandler. */
public class UnsupportedMediaFileException extends RuntimeException {
    public UnsupportedMediaFileException(String message) {
        super(message);
    }
}
