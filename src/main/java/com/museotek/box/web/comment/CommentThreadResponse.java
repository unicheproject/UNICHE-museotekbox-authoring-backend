package com.museotek.box.web.comment;

import com.museotek.box.application.comment.CommentThread;
import com.museotek.box.domain.comment.Comment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A thread: its first comment (with what it's about and whether it's resolved) and its replies. */
public record CommentThreadResponse(
        UUID id,
        String sceneKey,
        String stepKey,
        String body,
        String authorName,
        Instant createdAt,
        boolean resolved,
        String resolvedBy,
        Instant resolvedAt,
        List<CommentResponse> replies
) {

    public static CommentThreadResponse from(CommentThread thread) {
        Comment comment = thread.comment();
        List<CommentResponse> replies = thread.replies().stream()
                .map(CommentResponse::from)
                .toList();
        return new CommentThreadResponse(comment.getId(), comment.getSceneKey(), comment.getStepKey(), comment.getBody(),
                comment.getAuthorName(), comment.getCreatedAt(), comment.isResolved(), comment.getResolvedBy(),
                comment.getResolvedAt(), replies);
    }
}
