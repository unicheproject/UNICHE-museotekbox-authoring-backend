package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.Draft;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateDraftUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;

    public CreateDraftUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
    }

    public Draft execute(UUID orgId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId) {
        orgAccessGuard.requireAccess(orgId);
        scanObjectSupport.ensureRfidTagAvailable(rfidTag, null);

        Draft draft = new Draft();
        draft.setOrgId(orgId);
        draft.setName(name);
        draft.setRfidTag(rfidTag);
        draft.setReusable(reusable);
        draft.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        return scanObjectRepository.save(draft);
    }
}
