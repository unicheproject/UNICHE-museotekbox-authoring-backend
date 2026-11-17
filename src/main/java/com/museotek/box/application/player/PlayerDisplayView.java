package com.museotek.box.application.player;

import java.util.UUID;

/** A logged-in display as the player sees it: which session, and the one Box (and its org) it follows. */
public record PlayerDisplayView(UUID sessionId, Long boxId, UUID orgId) {
}
