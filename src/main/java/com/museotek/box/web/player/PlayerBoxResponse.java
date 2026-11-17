package com.museotek.box.web.player;

import com.museotek.box.application.player.PlayerBoxView;
import com.museotek.box.application.player.PlayerExperience;
import com.museotek.box.application.player.PlayerScanObject;

import java.util.List;

/** {@code pollSeconds}: how often the player project asks again, to pick up new publishes. */
public record PlayerBoxResponse(PlayerBoxSummaryResponse box, List<PlayerExperience> experiences,
                                List<PlayerBoxView.CardType> cardTypes, List<PlayerScanObject> scanObjects,
                                int pollSeconds) {

    public static PlayerBoxResponse from(PlayerBoxView view, int pollSeconds) {
        return new PlayerBoxResponse(
                new PlayerBoxSummaryResponse(view.box().id(), view.box().name()),
                view.experiences(),
                view.cardTypes(),
                view.scanObjects(),
                pollSeconds);
    }
}
