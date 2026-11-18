package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerBoxIdentity;
import com.museotek.box.application.player.VerifyBoxKeyUseCase;
import com.museotek.box.domain.box.InvalidBoxKeyException;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/**
 * The Box's websocket ({@code /ws/box}): {@code hello} with {@code payload.key}, its Box key from the CMS. While it is
 * connected the Box is online. It sends its {@code state} snapshots, and hears when to sync ({@code sync.required}),
 * whether its display is there ({@code display.presence}) and what the display reports ({@code scene.shown},
 * {@code video.ended}, {@code media.failed}).
 */
@Component
public class BoxSocketHandler extends DeviceSocketHandler<PlayerBoxIdentity> {

    private final VerifyBoxKeyUseCase verifyUseCase;
    private final Connections connections;

    BoxSocketHandler(VerifyBoxKeyUseCase verifyUseCase, Connections connections, LoginLimits limits,
                     PlayerTimer timer, PlayerSocketProperties properties, ObjectMapper json, Clock clock) {
        super("key", limits, timer, properties, json, clock);
        this.verifyUseCase = verifyUseCase;
        this.connections = connections;
    }

    @Override
    PlayerBoxIdentity verify(String key) {
        return verifyUseCase.execute(key);
    }

    @Override
    boolean isWrongToken(RuntimeException e) {
        return e instanceof InvalidBoxKeyException;
    }

    @Override
    String wrongTokenCode() {
        return "BOX_KEY_INVALID";
    }

    @Override
    Object limitKey(PlayerBoxIdentity box) {
        return box.boxId();
    }

    @Override
    Object helloOk(PlayerBoxIdentity box) {
        return Map.of("boxId", box.boxId(), "orgId", box.orgId());
    }

    @Override
    void loggedIn(DeviceConnection<PlayerBoxIdentity> connection) {
        connections.addBox(connection);
    }

    @Override
    void closed(DeviceConnection<PlayerBoxIdentity> connection) {
        connections.removeBox(connection);
    }

    @Override
    void handleLoggedIn(DeviceConnection<PlayerBoxIdentity> connection, String type, JsonNode message) {
        if (!DeviceMessages.STATE.equals(type)) {
            connection.sendError("MESSAGE_NOT_ALLOWED", "A box sends only state");
            return;
        }
        JsonNode state = message.get("payload");
        if (!BoxState.isValid(state)) {
            connection.sendError("MALFORMED_MESSAGE", "state needs a 'payload' object with a 'mode' of "
                    + String.join(", ", BoxState.MODES.stream().sorted().toList()));
            return;
        }
        connections.relayState(connection, state);
    }
}
