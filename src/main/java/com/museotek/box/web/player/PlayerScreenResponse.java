package com.museotek.box.web.player;

import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase.RedeemedScreen;
import com.museotek.box.application.player.PlayerScreenView;

import java.util.UUID;

/** A screen as the player project knows it: which one, and which museum's Boxes it may pick. */
public record PlayerScreenResponse(UUID screenId, UUID orgId, String name) {

    public static PlayerScreenResponse from(PlayerScreenView screen) {
        return new PlayerScreenResponse(screen.screenId(), screen.orgId(), screen.name());
    }

    /** Answer to a redeem: the only time the key is sent. */
    public record WithKey(UUID screenId, UUID orgId, String name, String key) {

        public static WithKey from(RedeemedScreen redeemed) {
            PlayerScreenView screen = redeemed.screen();
            return new WithKey(screen.screenId(), screen.orgId(), screen.name(), redeemed.key());
        }
    }
}
