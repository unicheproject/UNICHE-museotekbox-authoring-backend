package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.scanobject.CardsChangedEvent;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateScanObjectTypeUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;
    private final SpecialCardTypes specialCardTypes;
    private final ApplicationEventPublisher events;

    public UpdateScanObjectTypeUseCase(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository,
                                       SpecialCardTypes specialCardTypes, ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.specialCardTypes = specialCardTypes;
        this.events = events;
    }

    // universal / exit: null leaves the flag as it is.
    @Transactional
    public ScanObjectType execute(UUID orgId, Long id, String name, Boolean universal, Boolean exit) {
        orgAccessGuard.requireAccess(orgId);

        ScanObjectType type = scanObjectTypeRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ScanObjectTypeNotFoundException("No scan object type " + id + " for org " + orgId));

        boolean wasUniversal = type.isUniversal();
        boolean wasExit = type.isExit();
        type.setName(name);
        if (universal != null) {
            type.setUniversal(universal);
        }
        if (exit != null) {
            type.setExit(exit);
        }
        specialCardTypes.check(type, wasUniversal, wasExit);
        ScanObjectType saved = scanObjectTypeRepository.save(type);
        events.publishEvent(new CardsChangedEvent(orgId));
        return saved;
    }
}
