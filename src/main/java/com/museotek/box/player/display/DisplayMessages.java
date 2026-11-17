package com.museotek.box.player.display;

import com.museotek.box.application.player.PlayerDisplayView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The JSON messages the tool sends a display. Every message has the same envelope: {@code v}, {@code type},
 * {@code seq} (counts up per connection), {@code sentAt} and {@code payload}. The names are the protocol.
 */
final class DisplayMessages {

    static final int VERSION = 1;

    record Envelope(int v, String type, long seq, Instant sentAt, Object payload) {
    }

    record HelloOk(UUID sessionId, Long boxId, UUID orgId) {
    }

    /** The HTTP {@code ErrorEnvelope}'s fields. {@code requestId} is the connection's id in the logs. */
    record Error(String code, String message, List<String> details, String requestId) {
    }

    private DisplayMessages() {
    }

    static Object helloOk(PlayerDisplayView display) {
        return new HelloOk(display.sessionId(), display.boxId(), display.orgId());
    }

    static Object error(String code, String message, String connectionId) {
        return new Error(code, message, List.of(), connectionId);
    }

    /** {@code {}}: an empty record would need Jackson's FAIL_ON_EMPTY_BEANS turned off. */
    static Object empty() {
        return Map.of();
    }
}
