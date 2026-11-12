package com.museotek.box.application.screen;

import java.util.UUID;

/** A manager removed a screen. Published inside the revoke transaction; listeners act after it commits. */
public record ScreenRevokedEvent(UUID screenId) {
}
