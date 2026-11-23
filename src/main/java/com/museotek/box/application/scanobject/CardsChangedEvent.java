package com.museotek.box.application.scanobject;

import java.util.UUID;

/**
 * A card type or scan object of this org was created, changed or deleted. Every Box of the org gets all of the org's
 * cards when it syncs, so listeners act on all its Boxes after the change commits.
 */
public record CardsChangedEvent(UUID orgId) {
}
