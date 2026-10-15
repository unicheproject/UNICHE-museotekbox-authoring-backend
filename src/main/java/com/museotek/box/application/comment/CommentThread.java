package com.museotek.box.application.comment;

import com.museotek.box.domain.comment.Comment;

import java.time.Instant;
import java.util.List;

/** A thread: its first comment and the replies under it, oldest reply first. */
public record CommentThread(Comment comment, List<Comment> replies) {

    /**
     * The latest thing that happened in the thread: the comment, a reply, its resolve/reopen, or a
     * reply being deleted.
     */
    public Instant lastActivity() {
        Instant last = comment.getCreatedAt();
        if (comment.getStatusChangedAt() != null && comment.getStatusChangedAt().isAfter(last)) {
            last = comment.getStatusChangedAt();
        }
        if (comment.getReplyDeletedAt() != null && comment.getReplyDeletedAt().isAfter(last)) {
            last = comment.getReplyDeletedAt();
        }
        for (Comment reply : replies) {
            if (reply.getCreatedAt().isAfter(last)) {
                last = reply.getCreatedAt();
            }
        }
        return last;
    }
}
