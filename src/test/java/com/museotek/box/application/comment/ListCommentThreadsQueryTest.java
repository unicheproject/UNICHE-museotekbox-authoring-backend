package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.InvalidCommentException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListCommentThreadsQueryTest {

    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final ListCommentThreadsQuery query = new ListCommentThreadsQuery(projectAccessGuard, commentRepository);

    private final UUID projectId = UUID.randomUUID();

    // Thread A (scene 1, open) at T0 with a reply at T0+30; thread B (scene 2, resolved) at T0+10.
    private final Comment threadA = thread("1", false, T0.plusSeconds(0));
    private final Comment replyToA = reply(threadA, T0.plusSeconds(30));
    private final Comment threadB = thread("2", true, T0.plusSeconds(10));

    @Test
    void groupsRepliesUnderTheirThread_mostRecentActivityFirst() {
        stubComments();

        List<CommentThread> threads = query.execute(projectId, null, null, null);

        assertThat(threads).extracting(CommentThread::comment).containsExactly(threadA, threadB);
        assertThat(threads.get(0).replies()).containsExactly(replyToA);
        assertThat(threads.get(1).replies()).isEmpty();
    }

    @Test
    void filtersByScreenAndStatus() {
        stubComments();

        assertThat(query.execute(projectId, "2", null, null)).extracting(CommentThread::comment).containsExactly(threadB);
        assertThat(query.execute(projectId, null, "open", null)).extracting(CommentThread::comment).containsExactly(threadA);
        assertThat(query.execute(projectId, null, "RESOLVED", null)).extracting(CommentThread::comment).containsExactly(threadB);
        assertThat(query.execute(projectId, null, "all", null)).hasSize(2);
    }

    @Test
    void since_returnsOnlyThreadsWithSomethingNew() {
        stubComments();

        // Thread A's reply (T0+30) is new after T0+20; thread B (T0+10) isn't.
        assertThat(query.execute(projectId, null, null, T0.plusSeconds(20))).extracting(CommentThread::comment).containsExactly(threadA);
        assertThat(query.execute(projectId, null, null, T0.plusSeconds(60))).isEmpty();
    }

    @Test
    void since_countsAResolveAsActivity() {
        threadB.setResolvedAt(T0.plusSeconds(90));
        stubComments();

        assertThat(query.execute(projectId, null, null, T0.plusSeconds(60))).extracting(CommentThread::comment).containsExactly(threadB);
    }

    @Test
    void unknownStatus_isRefusedBeforeAnyLookup() {
        assertThatThrownBy(() -> query.execute(projectId, null, "closed", null)).isInstanceOf(InvalidCommentException.class);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void noAccess_touchesNothing() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(projectId, null, null, null)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(commentRepository);
    }

    private void stubComments() {
        when(commentRepository.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(threadA, threadB, replyToA));
    }

    private Comment thread(String sceneKey, boolean resolved, Instant createdAt) {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setProjectId(projectId);
        comment.setSceneKey(sceneKey);
        comment.setResolved(resolved);
        comment.setCreatedAt(createdAt);
        return comment;
    }

    private Comment reply(Comment thread, Instant createdAt) {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setProjectId(projectId);
        comment.setParentId(thread.getId());
        comment.setCreatedAt(createdAt);
        return comment;
    }
}
