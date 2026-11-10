package com.museotek.box.web.player;

import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase.RedeemedScreen;
import com.museotek.box.domain.screen.Screen;

import java.util.UUID;

/** A screen as the player project knows it: which one, and which museum's Boxes it may pick. */
public record PlayerScreenResponse(UUID screenId, UUID orgId, String name) {

    public static PlayerScreenResponse from(Screen screen) {
        return new PlayerScreenResponse(screen.getId(), screen.getOrgId(), screen.getName());
    }

    /** Answer to a redeem: the only time the key is sent. */
    public record WithKey(UUID screenId, UUID orgId, String name, String key) {

        public static WithKey from(RedeemedScreen redeemed) {
            Screen screen = redeemed.screen();
            return new WithKey(screen.getId(), screen.getOrgId(), screen.getName(), redeemed.key());
        }
    }
}
