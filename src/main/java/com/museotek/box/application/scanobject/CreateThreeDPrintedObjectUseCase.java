package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ThreeDPrintedObject;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateThreeDPrintedObjectUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;

    public CreateThreeDPrintedObjectUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
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
        return scanObjectRepository.save(threeDPrintedObject);
    }
}
