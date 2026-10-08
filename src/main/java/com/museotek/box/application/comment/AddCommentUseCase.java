package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Starts a comment thread on an experience. Anyone who can open the experience may comment. */
@Service
public class AddCommentUseCase {

    private final ProjectAccessGuard projectAccessGuard;
    private final CommentRepository commentRepository;
    private final CommentAuthor commentAuthor;

    public AddCommentUseCase(ProjectAccessGuard projectAccessGuard, CommentRepository commentRepository, CommentAuthor commentAuthor) {
        this.projectAccessGuard = projectAccessGuard;
        this.commentRepository = commentRepository;
        this.commentAuthor = commentAuthor;
    }

    public Comment execute(UUID projectId, String body, String sceneKey, String stepKey) {
        // Also makes sure the local project row exists, which comments.project_id points to.
        projectAccessGuard.requireAccess(projectId);
        CommentAuthor.Author author = commentAuthor.current();

        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setProjectId(projectId);
        comment.setSceneKey(sceneKey);
        comment.setStepKey(stepKey);
        comment.setBody(body);
        comment.setAuthorSubject(author.subject());
        comment.setAuthorName(author.name());
        comment.setCreatedAt(Instant.now());
        return commentRepository.save(comment);
    }
}
