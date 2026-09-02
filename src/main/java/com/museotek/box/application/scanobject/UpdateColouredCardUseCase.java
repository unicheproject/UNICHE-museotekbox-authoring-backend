package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.CardColour;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateColouredCardUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;

    public UpdateColouredCardUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
    }

    public ColouredCard execute(UUID orgId, Long scanObjectId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId, CardColour colour) {
        orgAccessGuard.requireAccess(orgId);

        ScanObject scanObject = scanObjectRepository.findByIdAndOrgId(scanObjectId, orgId)
                .orElseThrow(() -> new ScanObjectNotFoundException("No scan object " + scanObjectId + " for org " + orgId));
        if (!(scanObject instanceof ColouredCard card)) {
            throw new ScanObjectNotFoundException("No coloured card " + scanObjectId + " for org " + orgId);
        }

        scanObjectSupport.ensureRfidTagAvailable(rfidTag, scanObjectId);

        card.setName(name);
        card.setRfidTag(rfidTag);
        card.setReusable(reusable);
        card.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        card.setColour(colour);
        return scanObjectRepository.save(card);
    }
}
