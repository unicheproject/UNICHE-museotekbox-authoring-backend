package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateScanObjectTypeUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public CreateScanObjectTypeUseCase(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    public ScanObjectType execute(UUID orgId, String name) {
        orgAccessGuard.requireAccess(orgId);

        ScanObjectType type = new ScanObjectType();
        type.setOrgId(orgId);
        type.setName(name);
        return scanObjectTypeRepository.save(type);
    }
}
