package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListScanObjectsForOrgQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectRepository scanObjectRepository;

    public ListScanObjectsForOrgQuery(OrgAccessGuard orgAccessGuard, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectRepository = scanObjectRepository;
    }

    public List<ScanObject> execute(UUID orgId) {
        orgAccessGuard.requireAccess(orgId);
        return scanObjectRepository.findByOrgId(orgId);
    }
}
