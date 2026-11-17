package com.museotek.box.application.player;

import java.util.List;
import java.util.UUID;

/**
 * Everything a Box needs to run, apart from the experiences' content. Plain values only, no entities, so the player
 * package can hold and send it.
 */
public record PlayerBoxView(BoxSummary box, List<PlayerExperience> experiences, List<CardType> cardTypes,
                            List<PlayerScanObject> scanObjects) {

    /** The Box itself. No serial number: it is private and nobody downstream needs it. */
    public record BoxSummary(Long id, UUID orgId, String name) {
    }

    public record CardType(Long id, String name, boolean universal, boolean exit) {
    }

    public PlayerBoxView {
        experiences = List.copyOf(experiences);
        cardTypes = List.copyOf(cardTypes);
        scanObjects = List.copyOf(scanObjects);
    }
}
