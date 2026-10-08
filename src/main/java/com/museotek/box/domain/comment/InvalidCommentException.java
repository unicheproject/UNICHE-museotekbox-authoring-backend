package com.museotek.box.domain.comment;

/** A comment request that breaks a comment rule (e.g. a reply to a reply). Maps to 400 in GlobalExceptionHandler. */
public class InvalidCommentException extends RuntimeException {
    public InvalidCommentException(String message) {
        super(message);
    }
}
