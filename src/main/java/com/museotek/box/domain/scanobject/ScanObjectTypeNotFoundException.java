package com.museotek.box.domain.scanobject;

/** No ScanObjectType with the given id exists for the given org. Maps to 404 in GlobalExceptionHandler. */
public class ScanObjectTypeNotFoundException extends RuntimeException {
    public ScanObjectTypeNotFoundException(String message) {
        super(message);
    }
}
