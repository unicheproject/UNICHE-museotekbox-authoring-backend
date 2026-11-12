package com.museotek.box.player.screen;

import com.museotek.box.application.player.PlayerScreenView;

import java.util.List;
import java.util.UUID;

/** The JSON messages the tool sends a screen. The field names are the protocol (docs/communication.md). */
final class ScreenMessages {

    record SetupOk(String type, String screenKey, UUID screenId, String screenName) {
    }

    record AuthOk(String type, UUID screenId, String screenName) {
    }

    /** The HTTP {@code ErrorEnvelope}'s fields plus the type. {@code requestId} is the connection's id in the logs. */
    record Error(String type, String code, String message, List<String> details, String requestId) {
    }

    private ScreenMessages() {
    }

    static SetupOk setupOk(String screenKey, PlayerScreenView screen) {
        return new SetupOk("SETUP_OK", screenKey, screen.screenId(), screen.name());
    }

    static AuthOk authOk(PlayerScreenView screen) {
        return new AuthOk("AUTH_OK", screen.screenId(), screen.name());
    }

    static Error error(String code, String message, String connectionId) {
        return new Error("error", code, message, List.of(), connectionId);
    }
}
