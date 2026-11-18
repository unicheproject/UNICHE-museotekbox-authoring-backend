package com.museotek.box.player.socket;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The JSON messages the tool sends a display or a Box. Every message has the same envelope: {@code v}, {@code type},
 * {@code seq} (counts up per connection), {@code sentAt} and {@code payload}. The names are the protocol.
 */
final class DeviceMessages {

    static final int VERSION = 1;

    static final String HELLO = "hello";
    static final String HELLO_OK = "hello.ok";
    static final String SESSION_REPLACED = "session.replaced";
    static final String SESSION_REVOKED = "session.revoked";
    static final String BOX_PRESENCE = "box.presence";
    static final String DISPLAY_PRESENCE = "display.presence";
    static final String SYNC_REQUIRED = "sync.required";
    static final String IDLE_CHANGED = "idle.changed";

    record Envelope(int v, String type, long seq, Instant sentAt, Object payload) {
    }

    /** The HTTP {@code ErrorEnvelope}'s fields. {@code requestId} is the connection's id in the logs. */
    record Error(String code, String message, List<String> details, String requestId) {
    }

    private DeviceMessages() {
    }

    static Object error(String code, String message, String connectionId) {
        return new Error(code, message, List.of(), connectionId);
    }

    static Object boxPresence(boolean online) {
        return Map.of("online", online);
    }

    static Object displayPresence(boolean connected) {
        return Map.of("connected", connected);
    }
}
