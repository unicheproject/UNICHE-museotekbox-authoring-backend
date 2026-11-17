package com.museotek.box.domain.display;

/** A display's media link is not one the tool signed, has expired, or its display was revoked. */
public class InvalidMediaLinkException extends RuntimeException {
    public InvalidMediaLinkException(String message) {
        super(message);
    }
}
