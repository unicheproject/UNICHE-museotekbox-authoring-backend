package com.museotek.box.player.boundaryfixture;

import com.museotek.box.infrastructure.repository.BoxRepository;

/** Test fixture for PlayerBoundaryRulesTest: the shortcut the rule forbids. */
public class PlayerClassUsingARepository {

    private final BoxRepository boxRepository;

    public PlayerClassUsingARepository(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public Object boxName(Long boxId) {
        return boxRepository.findById(boxId);
    }
}
