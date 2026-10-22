package com.museotek.box.domain.template;

/** A template with an unknown type, a bad name or description, or defaults that aren't a JSON object. Maps to 400. */
public class InvalidTemplateException extends RuntimeException {
    public InvalidTemplateException(String message) {
        super(message);
    }
}
