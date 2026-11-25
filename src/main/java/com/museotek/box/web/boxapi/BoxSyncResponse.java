package com.museotek.box.web.boxapi;

import com.museotek.box.application.player.PlayerBoxView;
import com.museotek.box.application.player.PlayerScanObject;

import java.util.List;

/**
 * Everything a Box runs, in one answer: itself, the latest copy of each experience on it (content and the media the
 * Box plays itself), the org's card types and which tag is which card. Files come separately, from
 * {@code /box/media/{mediaId}/content}.
 */
public record BoxSyncResponse(PlayerBoxView.BoxSummary box, List<BoxExperienceResponse> experiences,
                              List<PlayerBoxView.CardType> cardTypes, List<PlayerScanObject> scanObjects) {

    public static BoxSyncResponse from(PlayerBoxView view) {
        return new BoxSyncResponse(view.box(),
                view.experiences().stream().map(BoxExperienceResponse::from).toList(),
                view.cardTypes(), view.scanObjects());
    }
}
