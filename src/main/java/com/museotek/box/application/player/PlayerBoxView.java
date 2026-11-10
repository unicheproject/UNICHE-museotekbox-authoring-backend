package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.scanobject.ScanObjectType;

import java.util.List;

/** Everything the player project needs to run a Box, apart from the experiences' content. */
public record PlayerBoxView(Box box, List<PlayerExperience> experiences, List<ScanObjectType> cardTypes,
                            List<PlayerScanObject> scanObjects) {

    public PlayerBoxView {
        experiences = List.copyOf(experiences);
        cardTypes = List.copyOf(cardTypes);
        scanObjects = List.copyOf(scanObjects);
    }
}
