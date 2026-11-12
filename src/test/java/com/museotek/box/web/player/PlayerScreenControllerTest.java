package com.museotek.box.web.player;

import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase;
import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase.RedeemedScreen;
import com.museotek.box.application.player.VerifyScreenKeyUseCase;
import com.museotek.box.domain.screen.InvalidScreenKeyException;
import com.museotek.box.application.player.PlayerScreenView;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerScreenControllerTest {

    private final RedeemScreenSetupCodeUseCase redeemUseCase = mock(RedeemScreenSetupCodeUseCase.class);
    private final VerifyScreenKeyUseCase verifyUseCase = mock(VerifyScreenKeyUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PlayerScreenController(redeemUseCase, verifyUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final PlayerScreenView screen = new PlayerScreenView(UUID.randomUUID(), UUID.randomUUID(), "Hall A screen");

    @Test
    void redeem_answersTheScreenAndItsKey() throws Exception {
        when(redeemUseCase.execute("K7QF-M2XR")).thenReturn(new RedeemedScreen(screen, "the-key"));

        mockMvc.perform(post("/player/v1/screen-setup-codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"K7QF-M2XR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.screenId").value(screen.screenId().toString()))
                .andExpect(jsonPath("$.orgId").value(screen.orgId().toString()))
                .andExpect(jsonPath("$.name").value("Hall A screen"))
                .andExpect(jsonPath("$.key").value("the-key"));
    }

    @Test
    void redeem_aCodeThatIsNotOpen_is404() throws Exception {
        when(redeemUseCase.execute(any())).thenThrow(new SetupCodeNotFoundException("no"));

        mockMvc.perform(post("/player/v1/screen-setup-codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AAAA-BBBB\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETUP_CODE_NOT_FOUND"));
    }

    @Test
    void verify_answersTheScreenWithoutAKey() throws Exception {
        when(verifyUseCase.execute("the-key")).thenReturn(screen);

        mockMvc.perform(post("/player/v1/screens/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"the-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value(screen.screenId().toString()))
                .andExpect(jsonPath("$.orgId").value(screen.orgId().toString()))
                .andExpect(jsonPath("$.key").doesNotExist());
    }

    @Test
    void verify_aBadKey_is401WithItsOwnCode() throws Exception {
        when(verifyUseCase.execute(any())).thenThrow(new InvalidScreenKeyException("no"));

        mockMvc.perform(post("/player/v1/screens/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SCREEN_KEY_INVALID"));
    }

    @Test
    void verify_withoutAKey_is400() throws Exception {
        mockMvc.perform(post("/player/v1/screens/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
