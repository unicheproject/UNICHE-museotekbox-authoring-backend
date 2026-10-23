package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.CommentNotFoundException;
import com.museotek.box.domain.comment.InvalidCommentException;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Replies to a comment thread. Replies are one level deep: only a thread's first comment takes replies. */
@Service
public class AddReplyUseCase {

    private final ProjectAccessGuard projectAccessGuard;
    private final CommentRepository commentRepository;
    private final CommentAuthor commentAuthor;
    private final Clock clock;

    public AddReplyUseCase(ProjectAccessGuard projectAccessGuard, CommentRepository commentRepository, CommentAuthor commentAuthor, Clock clock) {
        this.projectAccessGuard = projectAccessGuard;
        this.commentRepository = commentRepository;
        this.commentAuthor = commentAuthor;
        this.clock = clock;
    }

    public Comment execute(UUID projectId, UUID threadId, String body) {
        projectAccessGuard.requireAccess(projectId);

        Comment thread = commentRepository.findByIdAndProjectId(threadId, projectId)
                .orElseThrow(() -> new CommentNotFoundException("No comment " + threadId + " on experience " + projectId));
        if (thread.getParentId() != null) {
            throw new InvalidCommentException("Replies are one level deep: reply to the thread's first comment ("
                    + thread.getParentId() + ")");
        }
        CommentAuthor.Author author = commentAuthor.current();

        Comment reply = new Comment();
        reply.setId(UUID.randomUUID());
        reply.setProjectId(projectId);
        reply.setParentId(threadId);
        reply.setBody(body);
        reply.setAuthorSubject(author.subject());
        reply.setAuthorName(author.name());
        reply.setCreatedAt(Instant.now(clock));
        return commentRepository.save(reply);
    }
}
