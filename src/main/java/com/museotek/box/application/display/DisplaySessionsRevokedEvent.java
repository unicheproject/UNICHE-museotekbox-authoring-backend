package com.museotek.box.application.display;

import java.util.List;
import java.util.UUID;

/**
 * Display tokens stopped working: a manager revoked one, or its Box was deleted. Published inside that transaction;
 * listeners act after it commits.
 */
public record DisplaySessionsRevokedEvent(List<UUID> sessionIds) {
}
