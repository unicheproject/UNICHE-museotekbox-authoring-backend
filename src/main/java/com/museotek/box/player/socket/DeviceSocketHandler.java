package com.museotek.box.player.socket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the display's and the Box's websockets share. The first message must be {@code hello} with the device's token
 * in {@code payload}, within {@code login-timeout}; the token ties the connection to one Box. Every open connection is
 * pinged each {@code ping-interval} and closed when it hasn't answered for {@code pong-timeout}.
 *
 * @param <T> what the token says the device is
 */
abstract class DeviceSocketHandler<T> extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceSocketHandler.class);
    private static final String CONNECTION = DeviceConnection.class.getName();
    // Real tokens are 43 characters; anything far longer is not worth a database lookup.
    private static final int MAX_TOKEN_LENGTH = 64;

    private final String tokenField;
    private final LoginLimits limits;
    private final PlayerTimer timer;
    private final PlayerSocketProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final Set<DeviceConnection<T>> open = ConcurrentHashMap.newKeySet();

    DeviceSocketHandler(String tokenField, LoginLimits limits, PlayerTimer timer, PlayerSocketProperties properties,
                        ObjectMapper json, Clock clock) {
        this.tokenField = tokenField;
        this.limits = limits;
        this.timer = timer;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        timer.scheduleAtFixedRate(this::heartbeat, properties.pingInterval());
    }

    /** What the token says the device is. Throws when it is wrong; {@link #isWrongToken} says which throws mean that. */
    abstract T verify(String token);

    abstract boolean isWrongToken(RuntimeException e);

    /** The error code for a wrong token, e.g. {@code DISPLAY_TOKEN_INVALID}. */
    abstract String wrongTokenCode();

    /** What the reconnect limit counts logins by. */
    abstract Object limitKey(T device);

    abstract Object helloOk(T device);

    /** After {@code hello.ok} is sent: register the connection, tell the other side. */
    abstract void loggedIn(DeviceConnection<T> connection);

    /** A logged-in connection closed. */
    abstract void closed(DeviceConnection<T> connection);

    /** A message after login. Until the relay, nothing is accepted. */
    void handleLoggedIn(DeviceConnection<T> connection, String type, JsonNode message) {
        connection.sendError("MESSAGE_NOT_ALLOWED", "This message type is not accepted here");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        DeviceConnection<T> connection = new DeviceConnection<>(UUID.randomUUID().toString(), session, json, clock);
        session.getAttributes().put(CONNECTION, connection);
        open.add(connection);
        connection.withLogId(() -> {
            log.debug("Device connection opened ({})", getClass().getSimpleName());
            connection.setLoginDeadline(timer.schedule(
                    () -> connection.withLogId(() -> loginTimedOut(connection)), properties.loginTimeout()));
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DeviceConnection<T> connection = connection(session);
        connection.withLogId(() -> {
            try {
                handle(connection, message.getPayload());
            } catch (RuntimeException e) {
                log.error("Unexpected error while handling a device message", e);
                connection.sendError("INTERNAL_ERROR", "An unexpected error occurred");
                if (connection.device() == null) {
                    connection.close(CloseStatus.SERVER_ERROR);
                }
            }
        });
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        connection(session).ponged();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connection(session).withLogId(() -> log.debug("Device connection failed: {}", exception.getMessage()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        DeviceConnection<T> connection = connection(session);
        open.remove(connection);
        connection.withLogId(() -> {
            connection.stopLoginDeadline();
            if (connection.device() != null) {
                closed(connection);
            }
            log.debug("Device connection closed ({})", status.getCode());
        });
    }

    /** Pings every open connection, and closes those that stopped answering. */
    void heartbeat() {
        Instant deadline = clock.instant().minus(properties.pongTimeout());
        for (DeviceConnection<T> connection : open) {
            if (connection.lastPong().isBefore(deadline)) {
                connection.withLogId(() -> {
                    log.info("Closing a device connection that stopped answering pings");
                    connection.close(CloseStatus.SESSION_NOT_RELIABLE);
                });
            } else {
                connection.ping();
            }
        }
    }

    private void handle(DeviceConnection<T> connection, String payload) {
        JsonNode message = parse(payload);
        String type = textField(message, "type");
        if (connection.device() != null) {
            if (type == null) {
                connection.sendError("MALFORMED_MESSAGE", "Not a JSON object with a text 'type'");
            } else {
                handleLoggedIn(connection, type, message);
            }
            return;
        }
        if (!connection.stopLoginDeadline()) {
            return; // Too late: the deadline went off and the connection is being closed.
        }
        if (DeviceMessages.HELLO.equals(type)) {
            hello(connection, textField(objectField(message, "payload"), tokenField));
        } else if (type == null) {
            connection.refuse("MALFORMED_MESSAGE", "Not a JSON object with a text 'type'");
        } else {
            connection.refuse("MESSAGE_NOT_ALLOWED", "The first message must be hello");
        }
    }

    private void hello(DeviceConnection<T> connection, String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            connection.refuse("MALFORMED_MESSAGE", "hello needs a text 'payload." + tokenField + "'");
            return;
        }
        T device;
        try {
            device = verify(token);
        } catch (RuntimeException e) {
            if (!isWrongToken(e)) {
                throw e;
            }
            // No second try: a device has one token, and a wrong one won't get better.
            connection.refuse(wrongTokenCode(), "Unknown, replaced, revoked or expired " + tokenField);
            return;
        }
        if (!limits.allowLogin(limitKey(device))) {
            connection.refuse("TOO_MANY_RECONNECTS", "This device reconnects too often, try again in a minute");
            return;
        }
        connection.loggedIn(device);
        connection.send(DeviceMessages.HELLO_OK, helloOk(device));
        loggedIn(connection);
        log.debug("Device logged in: {}", device);
    }

    private void loginTimedOut(DeviceConnection<T> connection) {
        if (connection.device() == null && connection.isOpen()) {
            log.debug("Closing a device connection that did not say hello in time");
            connection.refuse("LOGIN_TIMEOUT", "No hello in time");
        }
    }

    @SuppressWarnings("unchecked")
    private DeviceConnection<T> connection(WebSocketSession session) {
        return (DeviceConnection<T>) session.getAttributes().get(CONNECTION);
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
