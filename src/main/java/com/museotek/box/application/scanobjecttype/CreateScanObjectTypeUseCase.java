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
    private final SpecialCardTypes specialCardTypes;

    public CreateScanObjectTypeUseCase(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository,
                                       SpecialCardTypes specialCardTypes) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
        this.specialCardTypes = specialCardTypes;
    }

    public ScanObjectType execute(UUID orgId, String name, boolean universal, boolean exit) {
        orgAccessGuard.requireAccess(orgId);

        ScanObjectType type = new ScanObjectType();
        type.setOrgId(orgId);
        type.setName(name);
        type.setUniversal(universal);
        type.setExit(exit);
        specialCardTypes.check(type, false, false);
        return scanObjectTypeRepository.save(type);
    }
}
