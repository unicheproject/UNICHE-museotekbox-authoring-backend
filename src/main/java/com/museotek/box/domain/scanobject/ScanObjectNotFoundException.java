package com.museotek.box.domain.scanobject;

/** No ScanObject with the given id exists for the given org (or it isn't the expected subtype). Maps to 404 in GlobalExceptionHandler. */
public class ScanObjectNotFoundException extends RuntimeException {
    public ScanObjectNotFoundException(String message) {
        super(message);
    }
}
