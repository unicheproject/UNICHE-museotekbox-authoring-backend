package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerDisplayView;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/**
 * The display's websocket ({@code /ws/display}): {@code hello} with {@code payload.token}, the display token from the
 * CMS. Only one display per Box is connected at a time, and the newest wins.
 */
@Component
public class DisplaySocketHandler extends DeviceSocketHandler<PlayerDisplayView> {

    private final VerifyDisplayTokenUseCase verifyUseCase;
    private final Connections connections;

    DisplaySocketHandler(VerifyDisplayTokenUseCase verifyUseCase, Connections connections, LoginLimits limits,
                         PlayerTimer timer, PlayerSocketProperties properties, ObjectMapper json, Clock clock) {
        super("token", limits, timer, properties, json, clock);
        this.verifyUseCase = verifyUseCase;
        this.connections = connections;
    }

    @Override
    PlayerDisplayView verify(String token) {
        return verifyUseCase.execute(token);
    }

    @Override
    boolean isWrongToken(RuntimeException e) {
        return e instanceof InvalidDisplayTokenException;
    }

    @Override
    String wrongTokenCode() {
        return "DISPLAY_TOKEN_INVALID";
    }

    @Override
    Object limitKey(PlayerDisplayView display) {
        return display.sessionId();
    }

    @Override
    Object helloOk(PlayerDisplayView display) {
        return Map.of("sessionId", display.sessionId(), "boxId", display.boxId(), "orgId", display.orgId());
    }

    @Override
    void loggedIn(DeviceConnection<PlayerDisplayView> connection) {
        connections.addDisplay(connection);
    }

    @Override
    void closed(DeviceConnection<PlayerDisplayView> connection) {
        connections.removeDisplay(connection);
    }
}
