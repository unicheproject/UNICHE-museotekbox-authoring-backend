package com.museotek.box.domain.box;

/** Another experience on the same Box already starts with this card. Maps to 409 in GlobalExceptionHandler. */
public class StartCardInUseException extends RuntimeException {
    public StartCardInUseException(String message) {
        super(message);
    }
}
