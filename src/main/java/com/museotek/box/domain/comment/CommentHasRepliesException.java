package com.museotek.box.domain.comment;

/** Its author can't delete a thread's first comment once others have replied. Maps to 409 in GlobalExceptionHandler. */
public class CommentHasRepliesException extends RuntimeException {
    public CommentHasRepliesException(String message) {
        super(message);
    }
}
