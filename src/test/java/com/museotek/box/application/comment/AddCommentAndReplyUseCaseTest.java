package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.CommentNotFoundException;
import com.museotek.box.domain.comment.InvalidCommentException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.junit.jupiter.api.Test;

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

class AddCommentAndReplyUseCaseTest {

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final CommentAuthor commentAuthor = mock(CommentAuthor.class);
    private final AddCommentUseCase addComment = new AddCommentUseCase(projectAccessGuard, commentRepository, commentAuthor);
    private final AddReplyUseCase addReply = new AddReplyUseCase(projectAccessGuard, commentRepository, commentAuthor);

    private final UUID projectId = UUID.randomUUID();

    @Test
    void addComment_savesAThreadStartWithItsScreenStepAndAuthor() {
        when(commentAuthor.current()).thenReturn(new CommentAuthor.Author("user-1", "Maria P."));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Comment comment = addComment.execute(projectId, "The red card does nothing here", "7", "i2");

        assertThat(comment.getId()).isNotNull();
        assertThat(comment.getProjectId()).isEqualTo(projectId);
        assertThat(comment.getParentId()).isNull();
        assertThat(comment.getSceneKey()).isEqualTo("7");
        assertThat(comment.getStepKey()).isEqualTo("i2");
        assertThat(comment.getAuthorSubject()).isEqualTo("user-1");
        assertThat(comment.getAuthorName()).isEqualTo("Maria P.");
        assertThat(comment.isResolved()).isFalse();
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    @Test
    void addComment_noAccess_savesNothing() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> addComment.execute(projectId, "x", null, null)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void addReply_savesUnderTheThread() {
        UUID threadId = UUID.randomUUID();
        when(commentRepository.findByIdAndProjectId(threadId, projectId)).thenReturn(Optional.of(comment(threadId, null)));
        when(commentAuthor.current()).thenReturn(new CommentAuthor.Author("user-2", "Salvador T."));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Comment reply = addReply.execute(projectId, threadId, "Fixed, try again");

        assertThat(reply.getParentId()).isEqualTo(threadId);
        assertThat(reply.getProjectId()).isEqualTo(projectId);
        assertThat(reply.getAuthorName()).isEqualTo("Salvador T.");
        assertThat(reply.getBody()).isEqualTo("Fixed, try again");
    }

    @Test
    void addReply_toAReply_isRefused() {
        UUID replyId = UUID.randomUUID();
        when(commentRepository.findByIdAndProjectId(replyId, projectId)).thenReturn(Optional.of(comment(replyId, UUID.randomUUID())));

        assertThatThrownBy(() -> addReply.execute(projectId, replyId, "x"))
                .isInstanceOf(InvalidCommentException.class)
                .hasMessageContaining("one level deep");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addReply_threadNotOnThisExperience_isNotFound() {
        UUID threadId = UUID.randomUUID();
        when(commentRepository.findByIdAndProjectId(threadId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addReply.execute(projectId, threadId, "x")).isInstanceOf(CommentNotFoundException.class);
    }

    private Comment comment(UUID id, UUID parentId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setProjectId(projectId);
        comment.setParentId(parentId);
        return comment;
    }
}
