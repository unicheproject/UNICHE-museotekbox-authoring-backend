package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.CommentDeleteForbiddenException;
import com.museotek.box.domain.comment.CommentHasRepliesException;
import com.museotek.box.domain.comment.CommentNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteCommentUseCaseTest {

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final CommentAuthor commentAuthor = mock(CommentAuthor.class);
    private final DeleteCommentUseCase useCase =
            new DeleteCommentUseCase(projectAccessGuard, catalogueClient, commentRepository, commentAuthor);

    private final UUID projectId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(
                new CatalogueProjectDto(projectId.toString(), orgId.toString(), "Visit", "visit", "ACTIVE", null, null, null));
    }

    @Test
    void author_deletesTheirOwnReply() {
        Comment reply = comment(UUID.randomUUID(), "user-1");
        stub(reply);
        callerIs("user-1", false, List.of());

        useCase.execute(projectId, reply.getId());

        verify(commentRepository).delete(reply);
    }

    @Test
    void author_deletesTheirOwnThreadStart_whileItHasNoReplies() {
        Comment start = comment(null, "user-1");
        stub(start);
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(start.getId())).thenReturn(List.of());
        callerIs("user-1", false, List.of());

        useCase.execute(projectId, start.getId());

        verify(commentRepository).delete(start);
    }

    @Test
    void author_cannotDeleteTheirThreadStart_onceSomeoneReplied() {
        Comment start = comment(null, "user-1");
        stub(start);
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(start.getId()))
                .thenReturn(List.of(comment(start.getId(), "user-2")));
        callerIs("user-1", false, List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, start.getId()))
                .isInstanceOf(CommentHasRepliesException.class);
        verify(commentRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAll(any());
    }

    @Test
    void someoneElse_cannotDeleteTheComment() {
        Comment reply = comment(UUID.randomUUID(), "user-1");
        stub(reply);
        callerIs("user-2", false, List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, reply.getId()))
                .isInstanceOf(CommentDeleteForbiddenException.class);
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void managerOfAnotherOrg_cannotDeleteTheComment() {
        Comment reply = comment(UUID.randomUUID(), "user-1");
        stub(reply);
        callerIs("user-2", false, List.of(UUID.randomUUID().toString()));

        assertThatThrownBy(() -> useCase.execute(projectId, reply.getId()))
                .isInstanceOf(CommentDeleteForbiddenException.class);
    }

    @Test
    void manager_deletesSomeoneElsesReply() {
        Comment reply = comment(UUID.randomUUID(), "user-1");
        stub(reply);
        callerIs("manager", false, List.of(orgId.toString()));

        useCase.execute(projectId, reply.getId());

        verify(commentRepository).delete(reply);
    }

    @Test
    void manager_deletesAThreadStart_withItsReplies_repliesFirst() {
        Comment start = comment(null, "user-1");
        List<Comment> replies = List.of(comment(start.getId(), "user-2"), comment(start.getId(), "user-3"));
        stub(start);
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(start.getId())).thenReturn(replies);
        callerIs("manager", false, List.of(orgId.toString()));

        useCase.execute(projectId, start.getId());

        var order = inOrder(commentRepository);
        order.verify(commentRepository).deleteAll(replies);
        order.verify(commentRepository).flush();
        order.verify(commentRepository).delete(start);
    }

    @Test
    void platformAdmin_deletesAnyComment() {
        Comment reply = comment(UUID.randomUUID(), "user-1");
        stub(reply);
        callerIs("admin", true, List.of());

        useCase.execute(projectId, reply.getId());

        verify(commentRepository).delete(reply);
    }

    @Test
    void commentOnAnotherExperience_isNotFound() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findByIdAndProjectId(commentId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(projectId, commentId))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void noAccessToTheExperience_touchesNoComments() {
        UUID otherProject = UUID.randomUUID();
        when(projectAccessGuard.requireAccess(otherProject)).thenThrow(new CatalogueForbiddenException("no"));

        assertThatThrownBy(() -> useCase.execute(otherProject, UUID.randomUUID()))
                .isInstanceOf(CatalogueForbiddenException.class);
        verifyNoInteractions(commentRepository);
    }

    private void callerIs(String subject, boolean platformAdmin, List<String> managedOrganisations) {
        when(commentAuthor.current()).thenReturn(new CommentAuthor.Author(subject, subject));
        when(catalogueClient.getMeAuthorization()).thenReturn(
                new CatalogueMeAuthorizationDto(subject, platformAdmin, managedOrganisations, List.of(), "etag", 60));
    }

    private void stub(Comment comment) {
        when(commentRepository.findByIdAndProjectId(comment.getId(), projectId)).thenReturn(Optional.of(comment));
    }

    private Comment comment(UUID parentId, String authorSubject) {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setProjectId(projectId);
        comment.setParentId(parentId);
        comment.setAuthorSubject(authorSubject);
        return comment;
    }
}
