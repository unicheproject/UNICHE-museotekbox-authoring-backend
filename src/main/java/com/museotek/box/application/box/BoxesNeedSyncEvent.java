package com.museotek.box.application.box;

import java.util.List;

/**
 * What these Boxes play changed: an experience on them was published again, assigned, unassigned, deleted or restored.
 * Listeners act after the change commits (a connected Box is told to sync, its display to reload its idle screen).
 */
public record BoxesNeedSyncEvent(List<Long> boxIds) {

    public BoxesNeedSyncEvent {
        boxIds = List.copyOf(boxIds);
    }
}
