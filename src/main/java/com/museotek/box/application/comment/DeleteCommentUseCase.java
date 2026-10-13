package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.CommentDeleteForbiddenException;
import com.museotek.box.domain.comment.CommentHasRepliesException;
import com.museotek.box.domain.comment.CommentNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Deletes a comment for good. Who may:
 * <ul>
 *   <li>its author: a reply any time, a thread's first comment only while nobody has replied
 *       (409 otherwise, so other people's replies are never lost);</li>
 *   <li>a manager of the experience's org, or a platform admin: any comment, for moderation (e.g.
 *       a pasted password). Deleting a thread's first comment deletes its replies with it.</li>
 * </ul>
 */
@Service
public class DeleteCommentUseCase {

    private final ProjectAccessGuard projectAccessGuard;
    private final CatalogueClient catalogueClient;
    private final CommentRepository commentRepository;
    private final CommentAuthor commentAuthor;

    public DeleteCommentUseCase(
            ProjectAccessGuard projectAccessGuard,
            CatalogueClient catalogueClient,
            CommentRepository commentRepository,
            CommentAuthor commentAuthor
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.catalogueClient = catalogueClient;
        this.commentRepository = commentRepository;
        this.commentAuthor = commentAuthor;
    }

    @Transactional
    public void execute(UUID projectId, UUID commentId) {
        CatalogueProjectDto project = projectAccessGuard.requireAccess(projectId);

        Comment comment = commentRepository.findByIdAndProjectId(commentId, projectId)
                .orElseThrow(() -> new CommentNotFoundException("No comment " + commentId + " on experience " + projectId));

        boolean manager = isManagerOf(project.orgId());
        boolean author = comment.getAuthorSubject().equals(commentAuthor.current().subject());
        if (!manager && !author) {
            throw new CommentDeleteForbiddenException("Only the author or a manager of the organisation can delete comment " + commentId);
        }

        if (comment.getParentId() == null) {
            List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(comment.getId());
            if (!replies.isEmpty()) {
                if (!manager) {
                    throw new CommentHasRepliesException("Comment " + commentId + " has replies, so it can't be deleted");
                }
                // Replies first: they point at the thread start.
                commentRepository.deleteAll(replies);
                commentRepository.flush();
            }
        }
        commentRepository.delete(comment);
    }

    private boolean isManagerOf(String orgId) {
        CatalogueMeAuthorizationDto me = catalogueClient.getMeAuthorization();
        return me.platformAdmin() || me.managedOrganisations().contains(orgId);
    }
}
