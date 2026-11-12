package com.museotek.box.player.screen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerScreenView;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

/** One open screen websocket: its id in the logs, the screen it logged in as (if any), its wrong attempts. */
final class ScreenConnection {

    private static final Logger log = LoggerFactory.getLogger(ScreenConnection.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_BYTES = 64 * 1024;

    private final String id;
    private final WebSocketSession session;
    private final ObjectMapper json;

    // Changed only while handling a message, and a socket's messages are handled one at a time.
    private int wrongSetupCodes;
    private int wrongKeys;

    // Also read by the timer thread and by the revoke listener.
    private volatile PlayerScreenView screen;
    private volatile ScheduledFuture<?> loginDeadline;

    ScreenConnection(String id, WebSocketSession session, ObjectMapper json) {
        this.id = id;
        // The message thread, the timer and the revoke listener may all send: the decorator makes that safe.
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        this.json = json;
    }

    String id() {
        return id;
    }

    /** Runs the action with this connection's id as the request id of its log lines, then restores the old one. */
    void withLogId(Runnable action) {
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(CorrelationIdFilter.MDC_KEY, id);
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    void send(Object message) {
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(message)));
        } catch (IOException | SessionLimitExceededException e) {
            log.debug("Could not send to the screen: {}", e.getMessage());
        }
    }

    void sendError(String code, String message) {
        send(ScreenMessages.error(code, message, id));
    }

    /** Sends the error, then closes with 1008 and the code as reason, so the screen knows not to just retry. */
    void refuse(String code, String message) {
        sendError(code, message);
        close(CloseStatus.POLICY_VIOLATION.withReason(code));
    }

    void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Could not close the screen connection: {}", e.getMessage());
        }
    }

    boolean isOpen() {
        return session.isOpen();
    }

    PlayerScreenView screen() {
        return screen;
    }

    void loggedIn(PlayerScreenView screen) {
        this.screen = screen;
    }

    int addWrongSetupCode() {
        return ++wrongSetupCodes;
    }

    int addWrongKey() {
        return ++wrongKeys;
    }

    void setLoginDeadline(ScheduledFuture<?> deadline) {
        this.loginDeadline = deadline;
    }

    /** Stops the login deadline. False if it already went off: the connection is being closed. */
    boolean stopLoginDeadline() {
        ScheduledFuture<?> deadline = loginDeadline;
        if (deadline == null) {
            return true;
        }
        return deadline.cancel(false) || deadline.isCancelled();
    }
}
