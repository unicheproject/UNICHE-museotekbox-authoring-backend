package com.museotek.box.application.publish;

import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Things worth fixing that don't stop a publish, unlike gaps ({@code ExperienceGaps}). They are about
 * the organisation, not the experience, so they can appear or go away without a new publish.
 * <ul>
 *   <li>the organisation has no Exit card type, so a visitor can't leave a visit before it ends.</li>
 * </ul>
 */
@Component
public class PublishWarnings {

    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public PublishWarnings(ScanObjectTypeRepository scanObjectTypeRepository) {
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    public List<String> find(UUID orgId) {
        List<String> warnings = new ArrayList<>();
        if (!scanObjectTypeRepository.existsByOrgIdAndExitTrue(orgId)) {
            warnings.add("the organisation has no Exit card, so visitors can't leave a visit before it ends");
        }
        return warnings;
    }
}
