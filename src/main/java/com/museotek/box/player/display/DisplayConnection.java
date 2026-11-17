package com.museotek.box.player.display;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerDisplayView;
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
import java.time.Clock;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/** One open display websocket: its id in the logs, the display it logged in as (if any), its message counter. */
final class DisplayConnection {

    private static final Logger log = LoggerFactory.getLogger(DisplayConnection.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_BYTES = 64 * 1024;

    private final String id;
    private final WebSocketSession session;
    private final ObjectMapper json;
    private final Clock clock;
    private final AtomicLong seq = new AtomicLong();

    // Also read by the timer thread and by the revoke listener.
    private volatile PlayerDisplayView display;
    private volatile ScheduledFuture<?> loginDeadline;

    DisplayConnection(String id, WebSocketSession session, ObjectMapper json, Clock clock) {
        this.id = id;
        // The message thread, the timer and other connections' threads may all send: the decorator makes that safe.
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        this.json = json;
        this.clock = clock;
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

    void send(String type, Object payload) {
        DisplayMessages.Envelope envelope = new DisplayMessages.Envelope(
                DisplayMessages.VERSION, type, seq.incrementAndGet(), clock.instant(), payload);
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(envelope)));
        } catch (IOException | SessionLimitExceededException e) {
            log.debug("Could not send to the display: {}", e.getMessage());
        }
    }

    void sendError(String code, String message) {
        send("error", DisplayMessages.error(code, message, id));
    }

    /** Sends the error, then closes with 1008 and the code as reason, so the display knows not to just retry. */
    void refuse(String code, String message) {
        sendError(code, message);
        close(CloseStatus.POLICY_VIOLATION.withReason(code));
    }

    /** Sends a message of the tool's own (e.g. {@code session.revoked}), then closes with 1008 and that reason. */
    void endWith(String type, String reason) {
        send(type, DisplayMessages.empty());
        close(CloseStatus.POLICY_VIOLATION.withReason(reason));
    }

    void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Could not close the display connection: {}", e.getMessage());
        }
    }

    boolean isOpen() {
        return session.isOpen();
    }

    PlayerDisplayView display() {
        return display;
    }

    void loggedIn(PlayerDisplayView display) {
        this.display = display;
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
