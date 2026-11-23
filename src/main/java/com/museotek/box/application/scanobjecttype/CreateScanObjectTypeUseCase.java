package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.scanobject.CardsChangedEvent;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateScanObjectTypeUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;
    private final SpecialCardTypes specialCardTypes;
    private final ApplicationEventPublisher events;

    public CreateScanObjectTypeUseCase(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository,
                                       SpecialCardTypes specialCardTypes, ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.specialCardTypes = specialCardTypes;
        this.events = events;
    }

    public ScanObjectType execute(UUID orgId, String name, boolean universal, boolean exit) {
        orgAccessGuard.requireAccess(orgId);

        ScanObjectType type = new ScanObjectType();
        type.setOrgId(orgId);
        type.setName(name);
        type.setUniversal(universal);
        type.setExit(exit);
        specialCardTypes.check(type, false, false);
        ScanObjectType saved = scanObjectTypeRepository.save(type);
        events.publishEvent(new CardsChangedEvent(orgId));
        return saved;
    }
}
