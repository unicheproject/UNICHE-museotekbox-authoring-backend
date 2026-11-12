package com.museotek.box.player.boundaryfixture;

import com.museotek.box.domain.box.Box;

/** Test fixture for PlayerBoundaryRulesTest: handles an entity, e.g. to send it to a screen. */
public class PlayerClassUsingAnEntity {

    public String serialOf(Box box) {
        return box.getSerialNumber();
    }
}
