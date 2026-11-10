package com.museotek.box.web.player;

import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase;
import com.museotek.box.application.player.VerifyScreenKeyUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Screens on {@code /player/v1}: the player project turns a setup code into a screen, and checks screen keys. */
@RestController
@RequestMapping("/player/v1")
public class PlayerScreenController {

    public record RedeemRequest(@NotBlank @Size(max = 32) String code) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 255) String key) {
    }

    private final RedeemScreenSetupCodeUseCase redeemUseCase;
    private final VerifyScreenKeyUseCase verifyUseCase;

    public PlayerScreenController(RedeemScreenSetupCodeUseCase redeemUseCase, VerifyScreenKeyUseCase verifyUseCase) {
        this.redeemUseCase = redeemUseCase;
        this.verifyUseCase = verifyUseCase;
    }

    // 404 SETUP_CODE_NOT_FOUND for an unknown, used, cancelled or expired code.
    @PostMapping("/screen-setup-codes/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerScreenResponse.WithKey redeem(@Valid @RequestBody RedeemRequest request) {
        return PlayerScreenResponse.WithKey.from(redeemUseCase.execute(request.code()));
    }

    // 401 SCREEN_KEY_INVALID for an unknown or removed screen.
    @PostMapping("/screens/verify")
    public PlayerScreenResponse verify(@Valid @RequestBody VerifyRequest request) {
        return PlayerScreenResponse.from(verifyUseCase.execute(request.key()));
    }
}
