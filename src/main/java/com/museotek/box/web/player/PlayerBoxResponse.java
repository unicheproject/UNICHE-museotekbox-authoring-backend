package com.museotek.box.web.player;

import com.museotek.box.application.player.PlayerBoxView;
import com.museotek.box.application.player.PlayerExperience;
import com.museotek.box.application.player.PlayerScanObject;
import com.museotek.box.domain.scanobject.ScanObjectType;

import java.util.List;

/** {@code pollSeconds}: how often the player project asks again, to pick up new publishes. */
public record PlayerBoxResponse(PlayerBoxSummaryResponse box, List<PlayerExperience> experiences,
                                List<CardType> cardTypes, List<PlayerScanObject> scanObjects, int pollSeconds) {

    public record CardType(Long id, String name, boolean universal, boolean exit) {

        static CardType from(ScanObjectType type) {
            return new CardType(type.getId(), type.getName(), type.isUniversal(), type.isExit());
        }
    }

    public static PlayerBoxResponse from(PlayerBoxView view, int pollSeconds) {
        return new PlayerBoxResponse(
                PlayerBoxSummaryResponse.from(view.box()),
                view.experiences(),
                view.cardTypes().stream().map(CardType::from).toList(),
                view.scanObjects(),
                pollSeconds);
    }
}
