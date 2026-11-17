package com.museotek.box.application.player;

import java.util.UUID;

/**
 * A logged-in display as the player sees it: which session, and the one Box (and its org) it follows.
 * {@code orgName} was copied when the display was opened; null for displays opened before that.
 */
public record PlayerDisplayView(UUID sessionId, Long boxId, UUID orgId, String orgName) {
}
