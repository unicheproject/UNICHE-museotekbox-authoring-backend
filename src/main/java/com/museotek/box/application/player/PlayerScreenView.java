package com.museotek.box.application.player;

import com.museotek.box.domain.screen.Screen;

import java.util.UUID;

/** A logged-in screen as the player sees it: which one, and which museum's Boxes it may use. */
public record PlayerScreenView(UUID screenId, UUID orgId, String name) {

    static PlayerScreenView from(Screen screen) {
        return new PlayerScreenView(screen.getId(), screen.getOrgId(), screen.getName());
    }
}
