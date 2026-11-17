package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a display shows while idle: the organisation, its Box and the experiences a visitor can start there, each
 * with its start card. Local tables only, never Catalogue: the display has no user login to ask it with.
 */
@Service
public class GetDisplayIdleQuery {

    public record StartCard(Long typeId, String name) {
    }

    public record IdleExperience(UUID projectId, String name, ExperienceOutput output, StartCard startCard) {
    }

    /** {@code orgName} is null for a display opened before the name was stored; open it again to fill it. */
    public record DisplayIdleView(String orgName, Long boxId, String boxName, BoxStatus boxStatus,
                                  List<IdleExperience> experiences) {

        public DisplayIdleView {
            experiences = List.copyOf(experiences);
        }
    }

    private final BoxRepository boxRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public GetDisplayIdleQuery(BoxRepository boxRepository,
                               PublishedExperienceRepository publishedExperienceRepository,
                               ScanObjectTypeRepository scanObjectTypeRepository) {
        this.boxRepository = boxRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    @Transactional(readOnly = true)
    public DisplayIdleView execute(PlayerDisplayView display) {
        // The token was just checked against this Box, so it only fails in a race with deleting the Box.
        Box box = boxRepository.findById(display.boxId())
                .orElseThrow(() -> new BoxNotFoundException("No box " + display.boxId()));
        Map<Long, String> cardNames = scanObjectTypeRepository.findByOrgId(box.getOrgId()).stream()
                .collect(Collectors.toMap(ScanObjectType::getId, ScanObjectType::getName));
        List<IdleExperience> experiences = publishedExperienceRepository.findLatestCopiesOnBox(box.getId()).stream()
                .map(copy -> new IdleExperience(copy.getProject().getId(), copy.getName(), copy.getOutput(),
                        new StartCard(copy.getStartCardTypeId(), cardNames.get(copy.getStartCardTypeId()))))
                .toList();
        return new DisplayIdleView(display.orgName(), box.getId(), box.getName(), box.getStatus(), experiences);
    }
}
