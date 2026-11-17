package com.museotek.box.player.display;

import com.museotek.box.application.display.DisplaySessionsRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The logged-in display connections, at most one per Box. A new display for a Box replaces the one before it (the
 * newest wins), and a display whose token is revoked, or whose Box is deleted, is closed straight away.
 */
@Component
class DisplayConnections {

    static final String SESSION_REPLACED = "session.replaced";
    static final String SESSION_REVOKED = "session.revoked";

    private static final Logger log = LoggerFactory.getLogger(DisplayConnections.class);

    private final Map<Long, DisplayConnection> byBox = new ConcurrentHashMap<>();

    /** Makes this the Box's display. The one before it, if any, is told it was replaced and closed. */
    void add(DisplayConnection connection) {
        Long boxId = connection.display().boxId();
        DisplayConnection previous = byBox.put(boxId, connection);
        if (previous != null && previous != connection) {
            previous.withLogId(() -> {
                log.info("Another display opened for box {}, closing this one", boxId);
                previous.endWith(SESSION_REPLACED, "SESSION_REPLACED");
            });
        }
    }

    /** Forgets a closed connection, unless a newer one already took its Box. */
    void remove(DisplayConnection connection) {
        byBox.remove(connection.display().boxId(), connection);
    }

    // After the commit only: a revoke that rolls back must not close anything.
    @TransactionalEventListener
    public void onSessionsRevoked(DisplaySessionsRevokedEvent event) {
        Set<UUID> revoked = new HashSet<>(event.sessionIds());
        for (DisplayConnection connection : byBox.values()) {
            if (revoked.contains(connection.display().sessionId())) {
                remove(connection);
                connection.withLogId(() -> {
                    log.info("Display {} was revoked, closing its connection", connection.display().sessionId());
                    connection.endWith(SESSION_REVOKED, "SESSION_REVOKED");
                });
            }
        }
    }
}
