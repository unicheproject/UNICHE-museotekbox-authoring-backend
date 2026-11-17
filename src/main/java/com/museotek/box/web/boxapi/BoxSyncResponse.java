package com.museotek.box.web.boxapi;

import com.museotek.box.application.player.PlayerBoxView;
import com.museotek.box.application.player.PlayerExperience;
import com.museotek.box.application.player.PlayerScanObject;

import java.util.List;

/**
 * What a Box keeps in step with: itself, the latest copy of each experience on it (download one when its version
 * changes), the org's card types and which tag is which card.
 */
public record BoxSyncResponse(PlayerBoxView.BoxSummary box, List<PlayerExperience> experiences,
                              List<PlayerBoxView.CardType> cardTypes, List<PlayerScanObject> scanObjects) {

    public static BoxSyncResponse from(PlayerBoxView view) {
        return new BoxSyncResponse(view.box(), view.experiences(), view.cardTypes(), view.scanObjects());
    }
}
