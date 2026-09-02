package com.museotek.box.domain.scanobject;

/** A ScanObject with this RFID tag already exists. Maps to 409 in GlobalExceptionHandler. */
public class DuplicateRfidTagException extends RuntimeException {
    public DuplicateRfidTagException(String message) {
        super(message);
    }
}
