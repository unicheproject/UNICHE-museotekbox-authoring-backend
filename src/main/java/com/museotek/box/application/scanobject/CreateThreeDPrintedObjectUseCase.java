package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ThreeDPrintedObject;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateThreeDPrintedObjectUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;
    private final ApplicationEventPublisher events;

    public CreateThreeDPrintedObjectUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository,
            ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
        this.events = events;
    }

    public ThreeDPrintedObject execute(UUID orgId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId, String modelRef) {
        orgAccessGuard.requireAccess(orgId);
        scanObjectSupport.ensureRfidTagAvailable(rfidTag, null);

        ThreeDPrintedObject threeDPrintedObject = new ThreeDPrintedObject();
        threeDPrintedObject.setOrgId(orgId);
        threeDPrintedObject.setName(name);
        threeDPrintedObject.setRfidTag(rfidTag);
        threeDPrintedObject.setReusable(reusable);
        threeDPrintedObject.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        threeDPrintedObject.setModelRef(modelRef);
        ThreeDPrintedObject saved = scanObjectRepository.save(threeDPrintedObject);
        events.publishEvent(new CardsChangedEvent(orgId));
        return saved;
    }
}
