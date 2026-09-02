package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.domain.scanobject.ThreeDPrintedObject;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateThreeDPrintedObjectUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;

    public UpdateThreeDPrintedObjectUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
    }

    public ThreeDPrintedObject execute(UUID orgId, Long scanObjectId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId, String modelRef) {
        orgAccessGuard.requireAccess(orgId);

        ScanObject scanObject = scanObjectRepository.findByIdAndOrgId(scanObjectId, orgId)
                .orElseThrow(() -> new ScanObjectNotFoundException("No scan object " + scanObjectId + " for org " + orgId));
        if (!(scanObject instanceof ThreeDPrintedObject threeDPrintedObject)) {
            throw new ScanObjectNotFoundException("No 3D printed object " + scanObjectId + " for org " + orgId);
        }

        scanObjectSupport.ensureRfidTagAvailable(rfidTag, scanObjectId);

        threeDPrintedObject.setName(name);
        threeDPrintedObject.setRfidTag(rfidTag);
        threeDPrintedObject.setReusable(reusable);
        threeDPrintedObject.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        threeDPrintedObject.setModelRef(modelRef);
        return scanObjectRepository.save(threeDPrintedObject);
    }
}
