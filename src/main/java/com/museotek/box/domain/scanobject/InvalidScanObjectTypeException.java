package com.museotek.box.domain.scanobject;

/** A card type that can't exist, e.g. both universal and the Exit card. Maps to 400 in GlobalExceptionHandler. */
public class InvalidScanObjectTypeException extends RuntimeException {
    public InvalidScanObjectTypeException(String message) {
        super(message);
    }
}
