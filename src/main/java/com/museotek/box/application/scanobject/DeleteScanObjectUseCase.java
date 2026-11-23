package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteScanObjectUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectRepository scanObjectRepository;
    private final ApplicationEventPublisher events;

    public DeleteScanObjectUseCase(OrgAccessGuard orgAccessGuard, ScanObjectRepository scanObjectRepository,
            ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectRepository = scanObjectRepository;
        this.events = events;
    }

    public void execute(UUID orgId, Long scanObjectId) {
        orgAccessGuard.requireAccess(orgId);
        ScanObject scanObject = scanObjectRepository.findByIdAndOrgId(scanObjectId, orgId)
                .orElseThrow(() -> new ScanObjectNotFoundException("No scan object " + scanObjectId + " for org " + orgId));
        scanObjectRepository.delete(scanObject);
        events.publishEvent(new CardsChangedEvent(orgId));
    }
}
