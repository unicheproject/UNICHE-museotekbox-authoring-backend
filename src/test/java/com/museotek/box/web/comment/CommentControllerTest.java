package com.museotek.box.web.comment;

import com.museotek.box.application.comment.AddCommentUseCase;
import com.museotek.box.application.comment.AddReplyUseCase;
import com.museotek.box.application.comment.ListCommentThreadsQuery;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP side only: request validation, response shape, parameter binding. The use cases are mocked.
class CommentControllerTest {

    private final AddCommentUseCase addCommentUseCase = mock(AddCommentUseCase.class);
    private final AddReplyUseCase addReplyUseCase = mock(AddReplyUseCase.class);
    private final ListCommentThreadsQuery listCommentThreadsQuery = mock(ListCommentThreadsQuery.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CommentController(addCommentUseCase, addReplyUseCase, listCommentThreadsQuery))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID projectId = UUID.randomUUID();

    @Test
    void create_answers201WithTheThread() throws Exception {
        Comment comment = comment(null, "The red card does nothing here");
        comment.setSceneKey("7");
        when(addCommentUseCase.execute(projectId, "The red card does nothing here", "7", "i2")).thenReturn(comment);

        mockMvc.perform(post("/api/v1/projects/{projectId}/comments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"The red card does nothing here\",\"sceneKey\":\"7\",\"stepKey\":\"i2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sceneKey").value("7"))
                .andExpect(jsonPath("$.authorName").value("Maria P."))
                .andExpect(jsonPath("$.resolved").value(false))
                .andExpect(jsonPath("$.replies").isEmpty());
    }

    @Test
    void create_blankOrTooLongBody_is400() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/comments", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/comments", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(addCommentUseCase);
    }

    @Test
    void reply_answers201WithTheReply() throws Exception {
        UUID threadId = UUID.randomUUID();
        when(addReplyUseCase.execute(projectId, threadId, "Fixed")).thenReturn(comment(threadId, "Fixed"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/comments/{commentId}/replies", projectId, threadId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Fixed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Fixed"));
    }

    @Test
    void list_bindsTheFilters() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/comments", projectId)
                        .param("scene", "7").param("status", "open").param("since", "2026-10-07T10:00:00Z"))
                .andExpect(status().isOk());

        verify(listCommentThreadsQuery).execute(eq(projectId), eq("7"), eq("open"), eq(Instant.parse("2026-10-07T10:00:00Z")));
    }

    @Test
    void list_badSince_is400() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/comments", projectId).param("since", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        verifyNoInteractions(listCommentThreadsQuery);
    }

    private Comment comment(UUID parentId, String body) {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setProjectId(projectId);
        comment.setParentId(parentId);
        comment.setBody(body);
        comment.setAuthorName("Maria P.");
        comment.setCreatedAt(Instant.now());
        return comment;
    }
}
