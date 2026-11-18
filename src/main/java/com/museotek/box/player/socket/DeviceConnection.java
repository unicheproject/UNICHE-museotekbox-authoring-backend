package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One open device websocket (a display or a Box): its id in the logs, the device it logged in as (if any), its message
 * counter and when it last answered a ping.
 *
 * @param <T> what the device's token says it is: a display or a Box, tied to one Box either way
 */
final class DeviceConnection<T> {

    private static final Logger log = LoggerFactory.getLogger(DeviceConnection.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_BYTES = 64 * 1024;

    private final String id;
    private final WebSocketSession session;
    private final ObjectMapper json;
    private final Clock clock;
    private final AtomicLong seq = new AtomicLong();

    // Also read by the timer thread and by other connections' threads.
    private volatile T device;
    private volatile ScheduledFuture<?> loginDeadline;
    private volatile Instant lastPong;

    DeviceConnection(String id, WebSocketSession session, ObjectMapper json, Clock clock) {
        this.id = id;
        // The message thread, the timer and other connections' threads may all send: the decorator makes that safe.
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        this.json = json;
        this.clock = clock;
        this.lastPong = clock.instant();
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

    /** Sends one message in the envelope: {@code v}, {@code type}, {@code seq}, {@code sentAt}, {@code payload}. */
    void send(String type, Object payload) {
        DeviceMessages.Envelope envelope = new DeviceMessages.Envelope(
                DeviceMessages.VERSION, type, seq.incrementAndGet(), clock.instant(), payload);
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(envelope)));
        } catch (IOException | SessionLimitExceededException e) {
            log.debug("Could not send to the device: {}", e.getMessage());
        }
    }

    void sendError(String code, String message) {
        send("error", DeviceMessages.error(code, message, id));
    }

    /** Sends the error, then closes with 1008 and the code as reason, so the device knows not to just retry. */
    void refuse(String code, String message) {
        sendError(code, message);
        close(CloseStatus.POLICY_VIOLATION.withReason(code));
    }

    /** Sends a message of the tool's own (e.g. {@code session.revoked}), then closes with 1008 and that reason. */
    void endWith(String type, String reason) {
        send(type, Map.of());
        close(CloseStatus.POLICY_VIOLATION.withReason(reason));
    }

    void ping() {
        try {
            session.sendMessage(new PingMessage());
        } catch (IOException | SessionLimitExceededException e) {
            log.debug("Could not ping the device: {}", e.getMessage());
        }
    }

    void ponged() {
        lastPong = clock.instant();
    }

    Instant lastPong() {
        return lastPong;
    }

    void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Could not close the device connection: {}", e.getMessage());
        }
    }

    boolean isOpen() {
        return session.isOpen();
    }

    T device() {
        return device;
    }

    void loggedIn(T device) {
        this.device = device;
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
