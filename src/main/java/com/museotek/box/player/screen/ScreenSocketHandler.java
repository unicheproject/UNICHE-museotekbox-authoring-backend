package com.museotek.box.player.screen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerScreenView;
import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase;
import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase.RedeemedScreen;
import com.museotek.box.application.player.VerifyScreenKeyUseCase;
import com.museotek.box.domain.screen.InvalidScreenKeyException;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/**
 * The screen's websocket. Until it has logged in, a connection may only send SETUP (a new screen trades its setup code
 * for its key) or AUTH (a known screen sends its key); see docs/communication.md, steps 1 and 2.
 */
@Component
public class ScreenSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreenSocketHandler.class);
    private static final String CONNECTION = ScreenConnection.class.getName();
    // Real codes are 9 characters and keys 43; anything far longer is not worth a database lookup.
    private static final int MAX_SETUP_CODE_LENGTH = 32;
    private static final int MAX_SCREEN_KEY_LENGTH = 64;

    private final RedeemScreenSetupCodeUseCase redeemUseCase;
    private final VerifyScreenKeyUseCase verifyUseCase;
    private final LoginLimits limits;
    private final ScreenConnections connections;
    private final PlayerTimer timer;
    private final PlayerSocketProperties properties;
    private final ObjectMapper json;

    ScreenSocketHandler(RedeemScreenSetupCodeUseCase redeemUseCase, VerifyScreenKeyUseCase verifyUseCase,
                        LoginLimits limits, ScreenConnections connections, PlayerTimer timer,
                        PlayerSocketProperties properties, ObjectMapper json) {
        this.redeemUseCase = redeemUseCase;
        this.verifyUseCase = verifyUseCase;
        this.limits = limits;
        this.connections = connections;
        this.timer = timer;
        this.properties = properties;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ScreenConnection connection = new ScreenConnection(UUID.randomUUID().toString(), session, json);
        session.getAttributes().put(CONNECTION, connection);
        connection.withLogId(() -> {
            log.debug("Screen connection opened");
            startLoginDeadline(connection);
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ScreenConnection connection = connection(session);
        connection.withLogId(() -> {
            try {
                handle(connection, message.getPayload());
            } catch (RuntimeException e) {
                log.error("Unexpected error while handling a screen message", e);
                connection.sendError("INTERNAL_ERROR", "An unexpected error occurred");
                if (connection.screen() == null) {
                    connection.close(CloseStatus.SERVER_ERROR);
                }
            }
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connection(session).withLogId(() -> log.debug("Screen connection failed: {}", exception.getMessage()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ScreenConnection connection = connection(session);
        connection.withLogId(() -> {
            connection.stopLoginDeadline();
            PlayerScreenView screen = connection.screen();
            if (screen != null) {
                connections.remove(screen.screenId(), connection);
            }
            log.debug("Screen connection closed ({})", status.getCode());
        });
    }

    private void handle(ScreenConnection connection, String payload) {
        JsonNode message = parse(payload);
        String type = textField(message, "type");
        if (connection.screen() != null) {
            // Nothing is accepted after login yet: the Box picker and the visits come in later steps.
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
        if ("SETUP".equals(type)) {
            setup(connection, textField(message, "setupCode"));
        } else if ("AUTH".equals(type)) {
            auth(connection, textField(message, "screenKey"));
        } else if (type == null) {
            connection.refuse("MALFORMED_MESSAGE", "Not a JSON object with a text 'type'");
        } else {
            connection.refuse("MESSAGE_NOT_ALLOWED", "The first message must be SETUP or AUTH");
        }
    }

    private void setup(ScreenConnection connection, String setupCode) {
        if (setupCode == null || setupCode.length() > MAX_SETUP_CODE_LENGTH) {
            connection.refuse("MALFORMED_MESSAGE", "SETUP needs a text 'setupCode'");
            return;
        }
        if (limits.setupPaused()) {
            connection.refuse("SETUP_PAUSED", "Too many wrong setup codes lately, try again in a few minutes");
            return;
        }
        RedeemedScreen redeemed;
        try {
            redeemed = redeemUseCase.execute(setupCode);
        } catch (SetupCodeNotFoundException e) {
            limits.recordFailedSetup();
            if (connection.addWrongSetupCode() >= properties.setupFailuresPerConnection()) {
                log.info("Closing a screen connection after {} wrong setup codes", properties.setupFailuresPerConnection());
                connection.refuse("TOO_MANY_ATTEMPTS", "Too many wrong setup codes");
            } else {
                connection.sendError("SETUP_CODE_NOT_FOUND", "Unknown, used, cancelled or expired setup code");
                startLoginDeadline(connection);
            }
            return;
        }
        loggedIn(connection, redeemed.screen());
        connection.send(ScreenMessages.setupOk(redeemed.key(), redeemed.screen()));
        log.info("Screen {} was set up", redeemed.screen().screenId());
    }

    private void auth(ScreenConnection connection, String screenKey) {
        if (screenKey == null || screenKey.length() > MAX_SCREEN_KEY_LENGTH) {
            connection.refuse("MALFORMED_MESSAGE", "AUTH needs a text 'screenKey'");
            return;
        }
        PlayerScreenView screen;
        try {
            screen = verifyUseCase.execute(screenKey);
        } catch (InvalidScreenKeyException e) {
            if (connection.addWrongKey() >= properties.keyFailuresPerConnection()) {
                log.info("Closing a screen connection after {} wrong screen keys", properties.keyFailuresPerConnection());
                connection.refuse("TOO_MANY_ATTEMPTS", "Too many wrong screen keys");
            } else {
                // Stays open: the screen may now ask for a setup code on this same connection.
                connection.sendError("SCREEN_KEY_INVALID", "Unknown or removed screen key");
                startLoginDeadline(connection);
            }
            return;
        }
        if (!limits.allowLogin(screen.screenId())) {
            connection.refuse("TOO_MANY_RECONNECTS", "This screen reconnects too often, try again in a minute");
            return;
        }
        loggedIn(connection, screen);
        connection.send(ScreenMessages.authOk(screen));
        log.debug("Screen {} logged in", screen.screenId());
    }

    private void loggedIn(ScreenConnection connection, PlayerScreenView screen) {
        connection.loggedIn(screen);
        connections.add(screen.screenId(), connection);
    }

    private void startLoginDeadline(ScreenConnection connection) {
        connection.setLoginDeadline(timer.schedule(
                () -> connection.withLogId(() -> loginTimedOut(connection)), properties.loginTimeout()));
    }

    private void loginTimedOut(ScreenConnection connection) {
        if (connection.screen() == null && connection.isOpen()) {
            log.debug("Closing a screen connection that did not log in in time");
            connection.refuse("LOGIN_TIMEOUT", "No SETUP or AUTH in time");
        }
    }

    private static ScreenConnection connection(WebSocketSession session) {
        return (ScreenConnection) session.getAttributes().get(CONNECTION);
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
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
