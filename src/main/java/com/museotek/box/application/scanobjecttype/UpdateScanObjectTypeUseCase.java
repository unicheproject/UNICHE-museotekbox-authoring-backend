package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateScanObjectTypeUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public UpdateScanObjectTypeUseCase(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    public ScanObjectType execute(UUID orgId, Long id, String name) {
        orgAccessGuard.requireAccess(orgId);

        ScanObjectType type = scanObjectTypeRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ScanObjectTypeNotFoundException("No scan object type " + id + " for org " + orgId));

        type.setName(name);
        return scanObjectTypeRepository.save(type);
    }
}
