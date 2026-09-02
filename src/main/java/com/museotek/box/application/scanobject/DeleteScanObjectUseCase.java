package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteScanObjectUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectRepository scanObjectRepository;

    public DeleteScanObjectUseCase(OrgAccessGuard orgAccessGuard, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectRepository = scanObjectRepository;
    }

    public void execute(UUID orgId, Long scanObjectId) {
        orgAccessGuard.requireAccess(orgId);
        ScanObject scanObject = scanObjectRepository.findByIdAndOrgId(scanObjectId, orgId)
                .orElseThrow(() -> new ScanObjectNotFoundException("No scan object " + scanObjectId + " for org " + orgId));
        scanObjectRepository.delete(scanObject);
    }
}
