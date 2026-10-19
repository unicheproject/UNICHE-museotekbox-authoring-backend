package com.museotek.box.domain.media;

/** A media name that is blank or too long. Maps to 400 in GlobalExceptionHandler. */
public class InvalidMediaNameException extends RuntimeException {
    public InvalidMediaNameException(String message) {
        super(message);
    }
}
