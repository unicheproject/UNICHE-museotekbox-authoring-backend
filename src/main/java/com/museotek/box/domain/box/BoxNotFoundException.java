package com.museotek.box.domain.box;

/** No Box with the given id exists for the given org. Maps to 404 in GlobalExceptionHandler. */
public class BoxNotFoundException extends RuntimeException {
    public BoxNotFoundException(String message) {
        super(message);
    }
}
