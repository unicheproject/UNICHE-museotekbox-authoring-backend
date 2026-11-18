package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerDisplayView;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

/**
 * The display's websocket ({@code /ws/display}): {@code hello} with {@code payload.token}, the display token from the
 * CMS. Only one display per Box is connected at a time, and the newest wins. It gets its Box's {@code state} snapshots
 * and reports back {@code scene.shown}, {@code video.ended} and {@code media.failed}, which go to the Box.
 */
@Component
public class DisplaySocketHandler extends DeviceSocketHandler<PlayerDisplayView> {

    private static final Set<String> FOR_THE_BOX = Set.of(
            DeviceMessages.SCENE_SHOWN, DeviceMessages.VIDEO_ENDED, DeviceMessages.MEDIA_FAILED);

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

    @Override
    void handleLoggedIn(DeviceConnection<PlayerDisplayView> connection, String type, JsonNode message) {
        if (!FOR_THE_BOX.contains(type)) {
            connection.sendError("MESSAGE_NOT_ALLOWED", "A display sends only scene.shown, video.ended or media.failed");
            return;
        }
        JsonNode payload = message.get("payload");
        // The Box drops reports about a session or scene instance that is no longer current, so it needs both.
        boolean needsBlock = !DeviceMessages.SCENE_SHOWN.equals(type);
        if (payload == null || !payload.isObject() || !payload.path("sessionId").isTextual()
                || !payload.path("instance").isIntegralNumber()
                || (needsBlock && !payload.path("blockKey").isTextual())) {
            connection.sendError("MALFORMED_MESSAGE", type + " needs a 'payload' with a text 'sessionId', a whole 'instance'"
                    + (needsBlock ? " and a text 'blockKey'" : ""));
            return;
        }
        connections.relayToBox(connection, type, payload);
    }
}
