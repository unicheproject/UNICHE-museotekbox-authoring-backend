package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A Box as the player project runs it: the latest copy of each experience on it, the org's card types and which tag is
 * which card. Reads local tables only, never Catalogue, so museums keep playing when Catalogue is down.
 */
@Service
public class GetBoxForPlayerQuery {

    private final BoxRepository boxRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final ScanObjectTypeRepository scanObjectTypeRepository;
    private final ScanObjectRepository scanObjectRepository;

    public GetBoxForPlayerQuery(
            BoxRepository boxRepository,
            PublishedExperienceRepository publishedExperienceRepository,
            ScanObjectTypeRepository scanObjectTypeRepository,
            ScanObjectRepository scanObjectRepository
    ) {
        this.boxRepository = boxRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.scanObjectRepository = scanObjectRepository;
    }

    // Read-only transaction: the scan objects' card types are lazy.
    @Transactional(readOnly = true)
    public PlayerBoxView execute(Long boxId) {
        Box box = boxRepository.findById(boxId).orElseThrow(() -> new BoxNotFoundException("No box " + boxId));

        List<PlayerExperience> experiences = publishedExperienceRepository.findLatestCopiesOnBox(boxId).stream()
                .map(PlayerExperience::of)
                .toList();
        // A card without a tag can't be scanned, and one without a type means nothing to a rule.
        List<PlayerScanObject> scanObjects = scanObjectRepository.findByOrgId(box.getOrgId()).stream()
                .filter(GetBoxForPlayerQuery::isPlayable)
                .map(card -> new PlayerScanObject(card.getRfidTag(), card.getScanObjectType().getId()))
                .toList();
        return new PlayerBoxView(box, experiences, scanObjectTypeRepository.findByOrgId(box.getOrgId()), scanObjects);
    }

    private static boolean isPlayable(ScanObject card) {
        return card.getRfidTag() != null && card.getScanObjectType() != null;
    }
}
