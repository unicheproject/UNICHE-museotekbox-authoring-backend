package com.museotek.box.architecture.fixture;

import com.museotek.box.player.boundaryfixture.PlayerClassUsingAQuery;

/** Test fixture for PlayerBoundaryRulesTest: authoring code reaching into the player. */
public class AuthoringClassUsingThePlayer {

    private final PlayerClassUsingAQuery player;

    public AuthoringClassUsingThePlayer(PlayerClassUsingAQuery player) {
        this.player = player;
    }
}
