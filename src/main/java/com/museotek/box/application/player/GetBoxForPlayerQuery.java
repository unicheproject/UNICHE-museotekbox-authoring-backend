package com.museotek.box.application.player;

import com.museotek.box.application.publish.PublishedContentMapper;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everything a Box runs: the latest copy of each experience on it with its content and the media the Box plays itself
 * (sounds, Box videos; the display fetches its own pictures and videos), the org's card types and which tag is which
 * card. Reads local tables only, never Catalogue, so museums keep playing when Catalogue is down.
 */
@Service
public class GetBoxForPlayerQuery {

    private final BoxRepository boxRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final ScanObjectTypeRepository scanObjectTypeRepository;
    private final ScanObjectRepository scanObjectRepository;
    private final MediaRepository mediaRepository;
    private final PublishedContentMapper contentMapper;

    public GetBoxForPlayerQuery(
            BoxRepository boxRepository,
            PublishedExperienceRepository publishedExperienceRepository,
            ScanObjectTypeRepository scanObjectTypeRepository,
            ScanObjectRepository scanObjectRepository,
            MediaRepository mediaRepository,
            PublishedContentMapper contentMapper
    ) {
        this.boxRepository = boxRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.scanObjectRepository = scanObjectRepository;
        this.mediaRepository = mediaRepository;
        this.contentMapper = contentMapper;
    }

    // Read-only transaction: the scan objects' card types are lazy.
    @Transactional(readOnly = true)
    public PlayerBoxView execute(Long boxId) {
        Box box = boxRepository.findById(boxId).orElseThrow(() -> new BoxNotFoundException("No box " + boxId));

        List<PlayerExperienceCopy> experiences = experiences(publishedExperienceRepository.findLatestCopiesOnBox(boxId));
        // A card without a tag can't be scanned, and one without a type means nothing to a rule.
        List<PlayerScanObject> scanObjects = scanObjectRepository.findByOrgId(box.getOrgId()).stream()
                .filter(GetBoxForPlayerQuery::isPlayable)
                .map(card -> new PlayerScanObject(card.getRfidTag(), card.getScanObjectType().getId()))
                .toList();
        List<PlayerBoxView.CardType> cardTypes = scanObjectTypeRepository.findByOrgId(box.getOrgId()).stream()
                .map(type -> new PlayerBoxView.CardType(type.getId(), type.getName(), type.isUniversal(), type.isExit()))
                .toList();
        return new PlayerBoxView(new PlayerBoxView.BoxSummary(box.getId(), box.getOrgId(), box.getName()),
                experiences, cardTypes, scanObjects);
    }

    // The media of all copies in one query; each copy lists only the files the Box plays itself.
    private List<PlayerExperienceCopy> experiences(List<PublishedExperience> copies) {
        List<List<UUID>> boxMediaIds = copies.stream().map(this::boxMediaIds).toList();
        Set<UUID> allIds = new LinkedHashSet<>();
        boxMediaIds.forEach(allIds::addAll);
        Map<UUID, PlayerExperienceCopy.MediaFile> files = mediaRepository.findAllById(allIds).stream()
                .collect(Collectors.toMap(Media::getId, GetBoxForPlayerQuery::file));

        List<PlayerExperienceCopy> experiences = new ArrayList<>();
        for (int i = 0; i < copies.size(); i++) {
            PublishedExperience copy = copies.get(i);
            List<PlayerExperienceCopy.MediaFile> media = boxMediaIds.get(i).stream()
                    .filter(files::containsKey)
                    .map(files::get)
                    .toList();
            experiences.add(new PlayerExperienceCopy(PlayerExperience.of(copy), copy.getPublishedAt(),
                    copy.getContent(), media));
        }
        return experiences;
    }

    private List<UUID> boxMediaIds(PublishedExperience copy) {
        Set<String> played = contentMapper.boxMediaIds(copy.getContent());
        return copy.getMediaIds().stream()
                .filter(played::contains)
                .map(UUID::fromString)
                .toList();
    }

    private static PlayerExperienceCopy.MediaFile file(Media media) {
        return new PlayerExperienceCopy.MediaFile(media.getId(), media.getKind(), media.getContentType(),
                media.getSizeBytes(), media.getSha256());
    }

    private static boolean isPlayable(ScanObject card) {
        return card.getRfidTag() != null && card.getScanObjectType() != null;
    }
}
