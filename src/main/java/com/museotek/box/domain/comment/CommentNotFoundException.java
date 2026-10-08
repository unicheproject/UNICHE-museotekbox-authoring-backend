package com.museotek.box.domain.comment;

/** No comment with this id on this experience. Maps to 404 in GlobalExceptionHandler. */
public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(String message) {
        super(message);
    }
}
