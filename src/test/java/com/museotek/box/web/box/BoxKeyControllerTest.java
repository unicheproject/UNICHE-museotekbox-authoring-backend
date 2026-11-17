package com.museotek.box.web.box;

import com.museotek.box.application.box.CreateBoxKeyUseCase;
import com.museotek.box.application.box.CreateBoxKeyUseCase.CreatedBoxKey;
import com.museotek.box.application.box.RemoveBoxKeyUseCase;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoxKeyControllerTest {

    private static final String PATH = "/api/v1/organisations/{orgId}/boxes/{boxId}/key";

    private final CreateBoxKeyUseCase createUseCase = mock(CreateBoxKeyUseCase.class);
    private final RemoveBoxKeyUseCase removeUseCase = mock(RemoveBoxKeyUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new BoxKeyController(createUseCase, removeUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID orgId = UUID.randomUUID();

    @Test
    void create_answersTheKeyOnce() throws Exception {
        when(createUseCase.execute(orgId, 12L))
                .thenReturn(new CreatedBoxKey("the-key", Instant.parse("2026-11-17T12:00:00Z")));

        mockMvc.perform(post(PATH, orgId, 12L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("the-key"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void create_forAnUnknownBox_is404() throws Exception {
        when(createUseCase.execute(orgId, 12L)).thenThrow(new BoxNotFoundException("no"));

        mockMvc.perform(post(PATH, orgId, 12L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOX_NOT_FOUND"));
    }

    @Test
    void remove_is204() throws Exception {
        mockMvc.perform(delete(PATH, orgId, 12L)).andExpect(status().isNoContent());

        verify(removeUseCase).execute(orgId, 12L);
    }

    @Test
    void remove_forAnUnknownBox_is404() throws Exception {
        doThrow(new BoxNotFoundException("no")).when(removeUseCase).execute(orgId, 12L);

        mockMvc.perform(delete(PATH, orgId, 12L)).andExpect(status().isNotFound());
    }
}
