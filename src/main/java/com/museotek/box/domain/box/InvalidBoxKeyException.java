package com.museotek.box.domain.box;

/** The Box key is unknown, or was replaced or removed. */
public class InvalidBoxKeyException extends RuntimeException {
    public InvalidBoxKeyException(String message) {
        super(message);
    }
}
