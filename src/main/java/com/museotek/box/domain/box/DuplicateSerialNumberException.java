package com.museotek.box.domain.box;

/** A Box with this serial number already exists locally. Maps to 409 in GlobalExceptionHandler. */
public class DuplicateSerialNumberException extends RuntimeException {
    public DuplicateSerialNumberException(String message) {
        super(message);
    }
}
