package com.museotek.box.application.comment;

import com.museotek.box.domain.comment.Comment;

import java.time.Instant;
import java.util.List;

/** A thread: its first comment and the replies under it, oldest reply first. */
public record CommentThread(Comment comment, List<Comment> replies) {

    /** The latest thing that happened in the thread: the comment, a reply, or its resolve/reopen. */
    public Instant lastActivity() {
        Instant last = comment.getCreatedAt();
        if (comment.getStatusChangedAt() != null && comment.getStatusChangedAt().isAfter(last)) {
            last = comment.getStatusChangedAt();
        }
        for (Comment reply : replies) {
            if (reply.getCreatedAt().isAfter(last)) {
                last = reply.getCreatedAt();
            }
        }
        return last;
    }
}
