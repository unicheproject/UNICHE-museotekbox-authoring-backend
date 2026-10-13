package com.museotek.box.domain.comment;

/** Only a comment's author, or a manager of the experience's org, can delete it. Maps to 403 in GlobalExceptionHandler. */
public class CommentDeleteForbiddenException extends RuntimeException {
    public CommentDeleteForbiddenException(String message) {
        super(message);
    }
}
