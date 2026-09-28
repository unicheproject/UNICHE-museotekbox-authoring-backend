package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetScanObjectTypeQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public GetScanObjectTypeQuery(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    public ScanObjectType execute(UUID orgId, Long id) {
        orgAccessGuard.requireAccess(orgId);
        return scanObjectTypeRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ScanObjectTypeNotFoundException("No scan object type " + id + " for org " + orgId));
    }
}
