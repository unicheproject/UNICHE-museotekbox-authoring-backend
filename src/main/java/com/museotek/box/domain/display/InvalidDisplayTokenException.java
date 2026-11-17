package com.museotek.box.domain.display;

/** The token is unknown, revoked or expired. */
public class InvalidDisplayTokenException extends RuntimeException {
    public InvalidDisplayTokenException(String message) {
        super(message);
    }
}
