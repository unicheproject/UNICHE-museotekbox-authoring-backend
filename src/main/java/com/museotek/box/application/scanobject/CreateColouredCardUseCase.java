package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.CardColour;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateColouredCardUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;
    private final ApplicationEventPublisher events;

    public CreateColouredCardUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository,
            ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
        this.events = events;
    }

    public ColouredCard execute(UUID orgId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId, CardColour colour) {
        orgAccessGuard.requireAccess(orgId);
        scanObjectSupport.ensureRfidTagAvailable(rfidTag, null);

        ColouredCard card = new ColouredCard();
        card.setOrgId(orgId);
        card.setName(name);
        card.setRfidTag(rfidTag);
        card.setReusable(reusable);
        card.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        card.setColour(colour);
        ColouredCard saved = scanObjectRepository.save(card);
        events.publishEvent(new CardsChangedEvent(orgId));
        return saved;
    }
}
