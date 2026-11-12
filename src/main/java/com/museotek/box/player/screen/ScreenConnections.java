package com.museotek.box.player.screen;

import com.museotek.box.application.screen.ScreenRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The logged-in screen connections by screen, so a screen a manager removes is closed straight away. */
@Component
class ScreenConnections {

    private static final Logger log = LoggerFactory.getLogger(ScreenConnections.class);

    private final Map<UUID, Set<ScreenConnection>> byScreen = new ConcurrentHashMap<>();

    void add(UUID screenId, ScreenConnection connection) {
        byScreen.compute(screenId, (id, connections) -> {
            Set<ScreenConnection> set = connections;
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
            }
            set.add(connection);
            return set;
        });
    }

    void remove(UUID screenId, ScreenConnection connection) {
        byScreen.computeIfPresent(screenId, (id, connections) -> {
            connections.remove(connection);
            if (connections.isEmpty()) {
                return null;
            }
            return connections;
        });
    }

    // After the commit only: a revoke that rolls back must not close anything.
    @TransactionalEventListener
    public void onScreenRevoked(ScreenRevokedEvent event) {
        Set<ScreenConnection> connections = byScreen.remove(event.screenId());
        if (connections == null) {
            return;
        }
        for (ScreenConnection connection : connections) {
            connection.withLogId(() -> {
                log.info("Screen {} was removed, closing its connection", event.screenId());
                connection.refuse("SCREEN_KEY_INVALID", "This screen was removed");
            });
        }
    }
}
