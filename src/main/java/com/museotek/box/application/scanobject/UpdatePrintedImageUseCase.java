package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.PrintedImage;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdatePrintedImageUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;

    public UpdatePrintedImageUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
    }

    public PrintedImage execute(UUID orgId, Long scanObjectId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId, String imageUrl) {
        orgAccessGuard.requireAccess(orgId);

        ScanObject scanObject = scanObjectRepository.findByIdAndOrgId(scanObjectId, orgId)
                .orElseThrow(() -> new ScanObjectNotFoundException("No scan object " + scanObjectId + " for org " + orgId));
        if (!(scanObject instanceof PrintedImage printedImage)) {
            throw new ScanObjectNotFoundException("No printed image " + scanObjectId + " for org " + orgId);
        }

        scanObjectSupport.ensureRfidTagAvailable(rfidTag, scanObjectId);

        printedImage.setName(name);
        printedImage.setRfidTag(rfidTag);
        printedImage.setReusable(reusable);
        printedImage.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        printedImage.setImageUrl(imageUrl);
        return scanObjectRepository.save(printedImage);
    }
}
