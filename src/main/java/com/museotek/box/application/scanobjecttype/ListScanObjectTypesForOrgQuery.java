package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListScanObjectTypesForOrgQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public ListScanObjectTypesForOrgQuery(OrgAccessGuard orgAccessGuard, ScanObjectTypeRepository scanObjectTypeRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    public List<ScanObjectType> execute(UUID orgId) {
        orgAccessGuard.requireAccess(orgId);
        return scanObjectTypeRepository.findByOrgId(orgId);
    }
}
