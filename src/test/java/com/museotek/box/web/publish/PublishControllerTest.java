package com.museotek.box.web.publish;

import com.museotek.box.application.publish.GetPublishStatusQuery;
import com.museotek.box.application.publish.PublishExperienceUseCase;
import com.museotek.box.application.publish.PublishStatus;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP side only: binding, response shape, error codes. The use case and query are mocked.
class PublishControllerTest {

    private final PublishExperienceUseCase publishUseCase = mock(PublishExperienceUseCase.class);
    private final GetPublishStatusQuery statusQuery = mock(GetPublishStatusQuery.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PublishController(publishUseCase, statusQuery))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID projectId = UUID.randomUUID();

    @Test
    void publish_answersTheVersionBoxesNowPlay() throws Exception {
        when(publishUseCase.execute(projectId)).thenReturn(
                new PublishStatus(3, 12, Instant.parse("2026-11-03T10:00:00Z"), false));

        mockMvc.perform(post("/api/v1/projects/{projectId}/publish", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.docVersion").value(12))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
    }

    @Test
    void publish_withGaps_is422WithTheGaps() throws Exception {
        when(publishUseCase.execute(projectId)).thenThrow(
                new ExperienceNotReadyException("has gaps", List.of("block 'b1' has no file chosen")));

        mockMvc.perform(post("/api/v1/projects/{projectId}/publish", projectId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_READY"))
                .andExpect(jsonPath("$.details[0]").value("block 'b1' has no file chosen"));
    }

    @Test
    void published_answersTheLatestVersion() throws Exception {
        when(statusQuery.execute(projectId)).thenReturn(
                new PublishStatus(3, 12, Instant.parse("2026-11-03T10:00:00Z"), true));

        mockMvc.perform(get("/api/v1/projects/{projectId}/published", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
    }

    @Test
    void published_neverPublished_is404NotPublished() throws Exception {
        when(statusQuery.execute(projectId)).thenThrow(new ExperienceNotPublishedException("never published"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/published", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_PUBLISHED"));
    }
}
