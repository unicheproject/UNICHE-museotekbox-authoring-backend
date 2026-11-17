package com.museotek.box.player.display;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerDisplayView;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Clock;
import java.util.UUID;

/**
 * The display's websocket ({@code /ws/display}). Its first message must be {@code hello} with the display token from
 * the CMS, within {@code login-timeout}. The token ties the connection to one Box; only one display per Box is
 * connected at a time, and the newest wins.
 */
@Component
public class DisplaySocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DisplaySocketHandler.class);
    private static final String CONNECTION = DisplayConnection.class.getName();
    // Real tokens are 43 characters; anything far longer is not worth a database lookup.
    private static final int MAX_TOKEN_LENGTH = 64;

    private final VerifyDisplayTokenUseCase verifyUseCase;
    private final LoginLimits limits;
    private final DisplayConnections connections;
    private final PlayerTimer timer;
    private final PlayerSocketProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    DisplaySocketHandler(VerifyDisplayTokenUseCase verifyUseCase, LoginLimits limits, DisplayConnections connections,
                         PlayerTimer timer, PlayerSocketProperties properties, ObjectMapper json, Clock clock) {
        this.verifyUseCase = verifyUseCase;
        this.limits = limits;
        this.connections = connections;
        this.timer = timer;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        DisplayConnection connection = new DisplayConnection(UUID.randomUUID().toString(), session, json, clock);
        session.getAttributes().put(CONNECTION, connection);
        connection.withLogId(() -> {
            log.debug("Display connection opened");
            connection.setLoginDeadline(timer.schedule(
                    () -> connection.withLogId(() -> loginTimedOut(connection)), properties.loginTimeout()));
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DisplayConnection connection = connection(session);
        connection.withLogId(() -> {
            try {
                handle(connection, message.getPayload());
            } catch (RuntimeException e) {
                log.error("Unexpected error while handling a display message", e);
                connection.sendError("INTERNAL_ERROR", "An unexpected error occurred");
                if (connection.display() == null) {
                    connection.close(CloseStatus.SERVER_ERROR);
                }
            }
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connection(session).withLogId(() -> log.debug("Display connection failed: {}", exception.getMessage()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        DisplayConnection connection = connection(session);
        connection.withLogId(() -> {
            connection.stopLoginDeadline();
            if (connection.display() != null) {
                connections.remove(connection);
            }
            log.debug("Display connection closed ({})", status.getCode());
        });
    }

    private void handle(DisplayConnection connection, String payload) {
        JsonNode message = parse(payload);
        String type = textField(message, "type");
        if (connection.display() != null) {
            // Nothing is accepted after hello yet: scene.shown, video.ended and media.failed come with the relay.
            if (type == null) {
                connection.sendError("MALFORMED_MESSAGE", "Not a JSON object with a text 'type'");
            } else {
                connection.sendError("MESSAGE_NOT_ALLOWED", "This message type is not accepted here");
            }
            return;
        }
        if (!connection.stopLoginDeadline()) {
            return; // Too late: the deadline went off and the connection is being closed.
        }
        if ("hello".equals(type)) {
            hello(connection, textField(objectField(message, "payload"), "token"));
        } else if (type == null) {
            connection.refuse("MALFORMED_MESSAGE", "Not a JSON object with a text 'type'");
        } else {
            connection.refuse("MESSAGE_NOT_ALLOWED", "The first message must be hello");
        }
    }

    private void hello(DisplayConnection connection, String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            connection.refuse("MALFORMED_MESSAGE", "hello needs a text 'payload.token'");
            return;
        }
        PlayerDisplayView display;
        try {
            display = verifyUseCase.execute(token);
        } catch (InvalidDisplayTokenException e) {
            // No second try: a display only has one token, and a wrong one won't get better.
            connection.refuse("DISPLAY_TOKEN_INVALID", "Unknown, revoked or expired display token");
            return;
        }
        if (!limits.allowLogin(display.sessionId())) {
            connection.refuse("TOO_MANY_RECONNECTS", "This display reconnects too often, try again in a minute");
            return;
        }
        connection.loggedIn(display);
        connection.send("hello.ok", DisplayMessages.helloOk(display));
        connections.add(connection);
        log.debug("Display {} logged in for box {}", display.sessionId(), display.boxId());
    }

    private void loginTimedOut(DisplayConnection connection) {
        if (connection.display() == null && connection.isOpen()) {
            log.debug("Closing a display connection that did not say hello in time");
            connection.refuse("LOGIN_TIMEOUT", "No hello in time");
        }
    }

    private static DisplayConnection connection(WebSocketSession session) {
        return (DisplayConnection) session.getAttributes().get(CONNECTION);
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static JsonNode objectField(JsonNode message, String name) {
        if (message == null) {
            return null;
        }
        JsonNode field = message.get(name);
        if (field == null || !field.isObject()) {
            return null;
        }
        return field;
    }

    private static String textField(JsonNode message, String name) {
        if (message == null) {
            return null;
        }
        JsonNode field = message.get(name);
        if (field == null || !field.isTextual()) {
            return null;
        }
        return field.asText();
    }
}
