package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeInUseException;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteScanObjectTypeUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;
    private final ScanObjectRepository scanObjectRepository;
    private final SceneRepository sceneRepository;
    private final RuleRepository ruleRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;

    public DeleteScanObjectTypeUseCase(
            OrgAccessGuard orgAccessGuard,
            ScanObjectTypeRepository scanObjectTypeRepository,
            ScanObjectRepository scanObjectRepository,
            SceneRepository sceneRepository,
            RuleRepository ruleRepository,
            PublishedExperienceRepository publishedExperienceRepository
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.scanObjectRepository = scanObjectRepository;
        this.sceneRepository = sceneRepository;
        this.ruleRepository = ruleRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
    }

    public void execute(UUID orgId, Long id) {
        orgAccessGuard.requireAccess(orgId);

        ScanObjectType type = scanObjectTypeRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ScanObjectTypeNotFoundException("No scan object type " + id + " for org " + orgId));

        // Referenced from three places (ScanObject.scanObjectType, Scene.initCardType,
        // Rule.scanObjectType) — none of those FKs cascade, so an unguarded delete would
        // surface as a raw DataIntegrityViolationException/500 instead of a clean 409.
        // A latest published copy has no FK but a Box plays it, so it counts too.
        if (scanObjectRepository.existsByScanObjectTypeId(id)
                || sceneRepository.existsByInitCardTypeId(id)
                || ruleRepository.existsByScanObjectTypeId(id)
                || publishedExperienceRepository.latestCopyUsesCardType(id)) {
            throw new ScanObjectTypeInUseException("Scan object type " + id
                    + " is still referenced by a scan object, scene, rule, or published experience");
        }

        scanObjectTypeRepository.delete(type);
    }
}
