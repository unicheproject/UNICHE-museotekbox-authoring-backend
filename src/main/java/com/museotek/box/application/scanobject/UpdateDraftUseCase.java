package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.Draft;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateDraftUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;
    private final ApplicationEventPublisher events;

    public UpdateDraftUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository,
            ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
        this.events = events;
    }

    public Draft execute(UUID orgId, Long scanObjectId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId) {
        orgAccessGuard.requireAccess(orgId);

        ScanObject scanObject = scanObjectRepository.findByIdAndOrgId(scanObjectId, orgId)
                .orElseThrow(() -> new ScanObjectNotFoundException("No scan object " + scanObjectId + " for org " + orgId));
        if (!(scanObject instanceof Draft draft)) {
            throw new ScanObjectNotFoundException("No draft " + scanObjectId + " for org " + orgId);
        }

        scanObjectSupport.ensureRfidTagAvailable(rfidTag, scanObjectId);

        draft.setName(name);
        draft.setRfidTag(rfidTag);
        draft.setReusable(reusable);
        draft.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        Draft saved = scanObjectRepository.save(draft);
        events.publishEvent(new CardsChangedEvent(orgId));
        return saved;
    }
}
