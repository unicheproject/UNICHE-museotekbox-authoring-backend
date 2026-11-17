package com.museotek.box.application.player;

import java.util.UUID;

/** A logged-in Box: which one, and its organisation. Taken from its key, never from a request. */
public record PlayerBoxIdentity(Long boxId, UUID orgId) {
}
