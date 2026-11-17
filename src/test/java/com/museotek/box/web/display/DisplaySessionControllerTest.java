package com.museotek.box.web.display;

import com.museotek.box.application.display.CreateDisplaySessionUseCase;
import com.museotek.box.application.display.CreateDisplaySessionUseCase.CreatedDisplaySession;
import com.museotek.box.application.display.ListDisplaySessionsQuery;
import com.museotek.box.application.display.RevokeDisplaySessionUseCase;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.DisplaySessionNotFoundException;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisplaySessionControllerTest {

    private static final String PATH = "/api/v1/organisations/{orgId}/boxes/{boxId}/display-sessions";

    private final CreateDisplaySessionUseCase createUseCase = mock(CreateDisplaySessionUseCase.class);
    private final ListDisplaySessionsQuery listQuery = mock(ListDisplaySessionsQuery.class);
    private final RevokeDisplaySessionUseCase revokeUseCase = mock(RevokeDisplaySessionUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DisplaySessionController(createUseCase, listQuery, revokeUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID orgId = UUID.randomUUID();

    @Test
    void create_answersTheTokenOnce() throws Exception {
        DisplaySession stored = session();
        when(createUseCase.execute(orgId, 12L)).thenReturn(new CreatedDisplaySession(stored, "the-token"));

        mockMvc.perform(post(PATH, orgId, 12L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(stored.getId().toString()))
                .andExpect(jsonPath("$.token").value("the-token"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    void list_neverShowsTheTokenOrItsHash() throws Exception {
        DisplaySession stored = session();
        stored.setLastSeenAt(Instant.parse("2026-11-16T11:59:00Z"));
        when(listQuery.execute(orgId, 12L)).thenReturn(List.of(stored));

        mockMvc.perform(get(PATH, orgId, 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stored.getId().toString()))
                .andExpect(jsonPath("$[0].lastSeenAt").exists())
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist());
    }

    @Test
    void revoke_is204_or404WhenUnknown() throws Exception {
        UUID sessionId = UUID.randomUUID();
        mockMvc.perform(delete(PATH + "/{sessionId}", orgId, 12L, sessionId))
                .andExpect(status().isNoContent());
        verify(revokeUseCase).execute(orgId, 12L, sessionId);

        doThrow(new DisplaySessionNotFoundException("gone")).when(revokeUseCase).execute(orgId, 12L, sessionId);
        mockMvc.perform(delete(PATH + "/{sessionId}", orgId, 12L, sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPLAY_SESSION_NOT_FOUND"));
    }

    private DisplaySession session() {
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(12L);
        session.setTokenHash("secret-hash");
        session.setCreatedBy("manager-subject");
        session.setCreatedAt(Instant.parse("2026-11-16T12:00:00Z"));
        session.setExpiresAt(Instant.parse("2026-12-16T12:00:00Z"));
        return session;
    }
}
