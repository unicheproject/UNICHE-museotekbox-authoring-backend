package com.museotek.box.player.socket;

import com.museotek.box.application.box.BoxKeyChangedEvent;
import com.museotek.box.application.box.BoxesNeedSyncEvent;
import com.museotek.box.application.display.DisplaySessionsRevokedEvent;
import com.museotek.box.application.player.BoxPresenceUseCase;
import com.museotek.box.application.player.PlayerBoxIdentity;
import com.museotek.box.application.player.PlayerDisplayView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is connected for each Box: at most one Box connection and one display connection. A newer connection replaces
 * the older one (the newest wins). Keeps the two sides told about each other ({@code box.presence},
 * {@code display.presence}) and the Box's online status in the database. In memory: only one copy of the app runs.
 */
@Component
class Connections {

    private static final Logger log = LoggerFactory.getLogger(Connections.class);

    private final Map<Long, DeviceConnection<PlayerDisplayView>> displays = new ConcurrentHashMap<>();
    private final Map<Long, DeviceConnection<PlayerBoxIdentity>> boxes = new ConcurrentHashMap<>();
    private final BoxPresenceUseCase presence;

    Connections(BoxPresenceUseCase presence) {
        this.presence = presence;
    }

    void addDisplay(DeviceConnection<PlayerDisplayView> connection) {
        Long boxId = connection.device().boxId();
        DeviceConnection<PlayerDisplayView> previous = displays.put(boxId, connection);
        if (previous != null && previous != connection) {
            previous.withLogId(() -> {
                log.info("Another display opened for box {}, closing this one", boxId);
                previous.endWith(DeviceMessages.SESSION_REPLACED, "SESSION_REPLACED");
            });
        }
        connection.send(DeviceMessages.BOX_PRESENCE, DeviceMessages.boxPresence(boxes.containsKey(boxId)));
        sendToBox(boxId, DeviceMessages.DISPLAY_PRESENCE, DeviceMessages.displayPresence(true));
    }

    /** Forgets a display that closed, unless a newer one already took its Box. */
    void removeDisplay(DeviceConnection<PlayerDisplayView> connection) {
        Long boxId = connection.device().boxId();
        if (displays.remove(boxId, connection)) {
            sendToBox(boxId, DeviceMessages.DISPLAY_PRESENCE, DeviceMessages.displayPresence(false));
        }
    }

    void addBox(DeviceConnection<PlayerBoxIdentity> connection) {
        Long boxId = connection.device().boxId();
        DeviceConnection<PlayerBoxIdentity> previous = boxes.put(boxId, connection);
        if (previous != null && previous != connection) {
            previous.withLogId(() -> {
                log.info("Box {} connected again, closing its older connection", boxId);
                previous.endWith(DeviceMessages.SESSION_REPLACED, "SESSION_REPLACED");
            });
        }
        presence.markOnline(boxId);
        connection.send(DeviceMessages.DISPLAY_PRESENCE, DeviceMessages.displayPresence(displays.containsKey(boxId)));
        sendToDisplay(boxId, DeviceMessages.BOX_PRESENCE, DeviceMessages.boxPresence(true));
    }

    /** Forgets a Box connection that closed, unless a newer one already took over; then the Box is offline. */
    void removeBox(DeviceConnection<PlayerBoxIdentity> connection) {
        Long boxId = connection.device().boxId();
        if (boxes.remove(boxId, connection)) {
            presence.markOffline(boxId);
            sendToDisplay(boxId, DeviceMessages.BOX_PRESENCE, DeviceMessages.boxPresence(false));
        }
    }

    // After the commit only: a revoke that rolls back must not close anything.
    @TransactionalEventListener
    public void onDisplaySessionsRevoked(DisplaySessionsRevokedEvent event) {
        Set<UUID> revoked = new HashSet<>(event.sessionIds());
        for (DeviceConnection<PlayerDisplayView> connection : displays.values()) {
            if (revoked.contains(connection.device().sessionId())) {
                removeDisplay(connection);
                connection.withLogId(() -> {
                    log.info("Display {} was revoked, closing its connection", connection.device().sessionId());
                    connection.endWith(DeviceMessages.SESSION_REVOKED, "SESSION_REVOKED");
                });
            }
        }
    }

    @TransactionalEventListener
    public void onBoxKeyChanged(BoxKeyChangedEvent event) {
        DeviceConnection<PlayerBoxIdentity> connection = boxes.get(event.boxId());
        if (connection == null) {
            return;
        }
        removeBox(connection);
        connection.withLogId(() -> {
            log.info("The key of box {} was replaced or removed, closing its connection", event.boxId());
            connection.endWith(DeviceMessages.SESSION_REVOKED, "SESSION_REVOKED");
        });
    }

    // fallbackExecution: deleting and restoring an experience publish this outside a transaction.
    @TransactionalEventListener(fallbackExecution = true)
    public void onBoxesNeedSync(BoxesNeedSyncEvent event) {
        for (Long boxId : event.boxIds()) {
            sendToBox(boxId, DeviceMessages.SYNC_REQUIRED, Map.of());
            sendToDisplay(boxId, DeviceMessages.IDLE_CHANGED, Map.of());
        }
    }

    // Nothing is connected right after startup, whatever the table says from before a restart or crash.
    @EventListener(ApplicationReadyEvent.class)
    public void resetPresence() {
        int boxCount = presence.markAllOffline();
        if (boxCount > 0) {
            log.info("Marked {} boxes offline at startup", boxCount);
        }
    }

    private void sendToBox(Long boxId, String type, Object payload) {
        DeviceConnection<PlayerBoxIdentity> connection = boxes.get(boxId);
        if (connection != null) {
            connection.send(type, payload);
        }
    }

    private void sendToDisplay(Long boxId, String type, Object payload) {
        DeviceConnection<PlayerDisplayView> connection = displays.get(boxId);
        if (connection != null) {
            connection.send(type, payload);
        }
    }
}
