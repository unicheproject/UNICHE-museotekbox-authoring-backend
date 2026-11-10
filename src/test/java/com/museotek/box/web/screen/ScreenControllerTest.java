package com.museotek.box.web.screen;

import com.museotek.box.application.screen.CancelScreenSetupCodeUseCase;
import com.museotek.box.application.screen.CreateScreenSetupCodeUseCase;
import com.museotek.box.application.screen.CreateScreenSetupCodeUseCase.CreatedSetupCode;
import com.museotek.box.application.screen.ListScreenSetupCodesQuery;
import com.museotek.box.application.screen.ListScreensQuery;
import com.museotek.box.application.screen.RevokeScreenUseCase;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScreenControllerTest {

    private final CreateScreenSetupCodeUseCase createUseCase = mock(CreateScreenSetupCodeUseCase.class);
    private final ListScreenSetupCodesQuery listCodesQuery = mock(ListScreenSetupCodesQuery.class);
    private final CancelScreenSetupCodeUseCase cancelUseCase = mock(CancelScreenSetupCodeUseCase.class);
    private final ListScreensQuery listScreensQuery = mock(ListScreensQuery.class);
    private final RevokeScreenUseCase revokeUseCase = mock(RevokeScreenUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ScreenController(createUseCase, listCodesQuery, cancelUseCase, listScreensQuery, revokeUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID orgId = UUID.randomUUID();

    @Test
    void createSetupCode_answersTheCodeOnce() throws Exception {
        ScreenSetupCode stored = setupCode();
        when(createUseCase.execute(orgId, "Hall A screen")).thenReturn(new CreatedSetupCode(stored, "K7QF-M2XR"));

        mockMvc.perform(post("/api/v1/organisations/{orgId}/screen-setup-codes", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hall A screen\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(stored.getId().toString()))
                .andExpect(jsonPath("$.name").value("Hall A screen"))
                .andExpect(jsonPath("$.code").value("K7QF-M2XR"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void createSetupCode_withoutAName_is400() throws Exception {
        mockMvc.perform(post("/api/v1/organisations/{orgId}/screen-setup-codes", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createUseCase);
    }

    @Test
    void listSetupCodes_neverShowsTheCode() throws Exception {
        when(listCodesQuery.execute(orgId)).thenReturn(List.of(setupCode()));

        mockMvc.perform(get("/api/v1/organisations/{orgId}/screen-setup-codes", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hall A screen"))
                .andExpect(jsonPath("$[0].code").doesNotExist())
                .andExpect(jsonPath("$[0].codeHash").doesNotExist());
    }

    @Test
    void cancelSetupCode_is204_or404WhenNotOpen() throws Exception {
        UUID codeId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/organisations/{orgId}/screen-setup-codes/{id}", orgId, codeId))
                .andExpect(status().isNoContent());
        verify(cancelUseCase).execute(orgId, codeId);

        doThrow(new SetupCodeNotFoundException("not open")).when(cancelUseCase).execute(orgId, codeId);
        mockMvc.perform(delete("/api/v1/organisations/{orgId}/screen-setup-codes/{id}", orgId, codeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETUP_CODE_NOT_FOUND"));
    }

    @Test
    void listScreens_showsLastSeen_butNoKey() throws Exception {
        Screen screen = new Screen();
        screen.setId(UUID.randomUUID());
        screen.setName("Hall A screen");
        screen.setKeyHash("secret-hash");
        screen.setLastSeenAt(Instant.parse("2026-11-10T11:59:00Z"));
        when(listScreensQuery.execute(orgId)).thenReturn(List.of(screen));

        mockMvc.perform(get("/api/v1/organisations/{orgId}/screens", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hall A screen"))
                .andExpect(jsonPath("$[0].lastSeenAt").exists())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist());
    }

    @Test
    void revokeScreen_is204() throws Exception {
        UUID screenId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/organisations/{orgId}/screens/{id}", orgId, screenId))
                .andExpect(status().isNoContent());

        verify(revokeUseCase).execute(orgId, screenId);
    }

    private ScreenSetupCode setupCode() {
        ScreenSetupCode code = new ScreenSetupCode();
        code.setId(UUID.randomUUID());
        code.setScreenName("Hall A screen");
        code.setCodeHash("hash");
        code.setCreatedAt(Instant.parse("2026-11-10T12:00:00Z"));
        code.setExpiresAt(Instant.parse("2026-11-13T12:00:00Z"));
        return code;
    }
}
