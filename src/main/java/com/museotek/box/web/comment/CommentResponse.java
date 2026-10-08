package com.museotek.box.web.comment;

import com.museotek.box.domain.comment.Comment;

import java.time.Instant;
import java.util.UUID;

/** A reply, as shown under its thread. */
public record CommentResponse(UUID id, String body, String authorName, Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getBody(), comment.getAuthorName(), comment.getCreatedAt());
    }
}
