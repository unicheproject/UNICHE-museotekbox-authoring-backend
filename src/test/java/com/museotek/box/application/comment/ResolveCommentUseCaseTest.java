package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.CommentNotFoundException;
import com.museotek.box.domain.comment.InvalidCommentException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResolveCommentUseCaseTest {

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final CommentAuthor commentAuthor = mock(CommentAuthor.class);
    private final ResolveCommentUseCase useCase = new ResolveCommentUseCase(projectAccessGuard, commentRepository, commentAuthor);

    private final UUID projectId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();

    @Test
    void resolve_recordsWhoAndWhen_andReturnsTheThreadWithReplies() {
        Comment thread = thread(false);
        Comment reply = new Comment();
        stub(thread);
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(threadId)).thenReturn(List.of(reply));
        when(commentAuthor.current()).thenReturn(new CommentAuthor.Author("user-2", "Salvador T."));

        CommentThread result = useCase.execute(projectId, threadId, true);

        assertThat(thread.isResolved()).isTrue();
        assertThat(thread.getResolvedBy()).isEqualTo("Salvador T.");
        assertThat(thread.getResolvedAt()).isNotNull();
        assertThat(thread.getStatusChangedAt()).isEqualTo(thread.getResolvedAt());
        assertThat(result.replies()).containsExactly(reply);
    }

    @Test
    void reopen_clearsWhoAndWhen_butRecordsTheStatusChange() {
        Comment thread = thread(true);
        thread.setResolvedBy("Salvador T.");
        thread.setResolvedAt(Instant.parse("2026-10-07T10:00:00Z"));
        stub(thread);

        useCase.execute(projectId, threadId, false);

        assertThat(thread.isResolved()).isFalse();
        assertThat(thread.getResolvedBy()).isNull();
        assertThat(thread.getResolvedAt()).isNull();
        assertThat(thread.getStatusChangedAt()).isAfter(Instant.parse("2026-10-07T10:00:00Z"));
    }

    @Test
    void resolvingAnAlreadyResolvedThread_changesNothing() {
        Comment thread = thread(true);
        thread.setResolvedBy("Maria P.");
        stub(thread);

        useCase.execute(projectId, threadId, true);

        assertThat(thread.getResolvedBy()).isEqualTo("Maria P.");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void aReply_cantBeResolved() {
        Comment reply = thread(false);
        reply.setParentId(UUID.randomUUID());
        when(commentRepository.findByIdAndProjectId(threadId, projectId)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> useCase.execute(projectId, threadId, true)).isInstanceOf(InvalidCommentException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void notOnThisExperience_isNotFound() {
        when(commentRepository.findByIdAndProjectId(threadId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(projectId, threadId, true)).isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void noAccess_touchesNothing() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(projectId, threadId, true)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(commentRepository);
    }

    private void stub(Comment thread) {
        when(commentRepository.findByIdAndProjectId(threadId, projectId)).thenReturn(Optional.of(thread));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Comment thread(boolean resolved) {
        Comment comment = new Comment();
        comment.setId(threadId);
        comment.setProjectId(projectId);
        comment.setResolved(resolved);
        comment.setCreatedAt(Instant.parse("2026-10-07T09:00:00Z"));
        return comment;
    }
}
