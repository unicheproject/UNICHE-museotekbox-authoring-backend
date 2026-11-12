package com.museotek.box.player.boundaryfixture;

import com.museotek.box.application.player.GetBoxForPlayerQuery;
import com.museotek.box.application.player.PlayerBoxView;

/** Test fixture for PlayerBoundaryRulesTest: the allowed way, through a query and its view. */
public class PlayerClassUsingAQuery {

    private final GetBoxForPlayerQuery getBoxForPlayer;

    public PlayerClassUsingAQuery(GetBoxForPlayerQuery getBoxForPlayer) {
        this.getBoxForPlayer = getBoxForPlayer;
    }

    public PlayerBoxView box(Long boxId) {
        return getBoxForPlayer.execute(boxId);
    }
}
