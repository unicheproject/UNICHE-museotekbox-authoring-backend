package com.museotek.box.domain.scanobject;

/** A ScanObjectType still has a ScanObject, Scene, or Rule referencing it. Maps to 409 in GlobalExceptionHandler. */
public class ScanObjectTypeInUseException extends RuntimeException {
    public ScanObjectTypeInUseException(String message) {
        super(message);
    }
}
