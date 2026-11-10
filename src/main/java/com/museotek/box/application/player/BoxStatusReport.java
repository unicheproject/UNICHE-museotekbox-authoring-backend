package com.museotek.box.application.player;

import java.time.Instant;
import java.util.UUID;

/**
 * What a Box is doing now, as the player project reports it when a Box connects or drops and when a visit starts,
 * changes scene or ends. {@code runningProjectId} null = idle. {@code at} = when it happened.
 */
public record BoxStatusReport(boolean online, UUID runningProjectId, String sceneKey, Instant at) {
}
