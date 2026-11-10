package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** An org's Boxes, for a screen's box picker. The player project checked the screen belongs to that org. */
@Service
public class ListOrgBoxesForPlayerQuery {

    private final BoxRepository boxRepository;

    public ListOrgBoxesForPlayerQuery(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public List<Box> execute(UUID orgId) {
        return boxRepository.findByOrgId(orgId);
    }
}
