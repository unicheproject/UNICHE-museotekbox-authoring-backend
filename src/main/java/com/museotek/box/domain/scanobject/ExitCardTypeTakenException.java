package com.museotek.box.domain.scanobject;

/** The org already has an Exit card type; there can be only one. Maps to 409 in GlobalExceptionHandler. */
public class ExitCardTypeTakenException extends RuntimeException {
    public ExitCardTypeTakenException(String message) {
        super(message);
    }
}
