package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.CommentNotFoundException;
import com.museotek.box.domain.comment.InvalidCommentException;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resolves or reopens a comment thread. Anyone who can open the experience may do either. Only a
 * thread's first comment carries the status. Asking for the status it already has changes nothing,
 * so a double click doesn't overwrite who resolved it.
 */
@Service
public class ResolveCommentUseCase {

    private final ProjectAccessGuard projectAccessGuard;
    private final CommentRepository commentRepository;
    private final CommentAuthor commentAuthor;

    public ResolveCommentUseCase(ProjectAccessGuard projectAccessGuard, CommentRepository commentRepository, CommentAuthor commentAuthor) {
        this.projectAccessGuard = projectAccessGuard;
        this.commentRepository = commentRepository;
        this.commentAuthor = commentAuthor;
    }

    public CommentThread execute(UUID projectId, UUID commentId, boolean resolved) {
        projectAccessGuard.requireAccess(projectId);

        Comment comment = commentRepository.findByIdAndProjectId(commentId, projectId)
                .orElseThrow(() -> new CommentNotFoundException("No comment " + commentId + " on experience " + projectId));
        if (comment.getParentId() != null) {
            throw new InvalidCommentException("Only a thread's first comment can be resolved or reopened ("
                    + comment.getParentId() + ")");
        }

        if (comment.isResolved() != resolved) {
            Instant now = Instant.now();
            comment.setResolved(resolved);
            comment.setStatusChangedAt(now);
            if (resolved) {
                comment.setResolvedBy(commentAuthor.current().name());
                comment.setResolvedAt(now);
            } else {
                comment.setResolvedBy(null);
                comment.setResolvedAt(null);
            }
            comment = commentRepository.save(comment);
        }

        List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(comment.getId());
        return new CommentThread(comment, replies);
    }
}
