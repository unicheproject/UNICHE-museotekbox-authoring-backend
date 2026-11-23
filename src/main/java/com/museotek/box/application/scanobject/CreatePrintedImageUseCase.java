package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.PrintedImage;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreatePrintedImageUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScanObjectSupport scanObjectSupport;
    private final ScanObjectRepository scanObjectRepository;
    private final ApplicationEventPublisher events;

    public CreatePrintedImageUseCase(OrgAccessGuard orgAccessGuard, ScanObjectSupport scanObjectSupport, ScanObjectRepository scanObjectRepository,
            ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.scanObjectSupport = scanObjectSupport;
        this.scanObjectRepository = scanObjectRepository;
        this.events = events;
    }

    public PrintedImage execute(UUID orgId, String name, String rfidTag, boolean reusable, Long scanObjectTypeId, String imageUrl) {
        orgAccessGuard.requireAccess(orgId);
        scanObjectSupport.ensureRfidTagAvailable(rfidTag, null);

        PrintedImage printedImage = new PrintedImage();
        printedImage.setOrgId(orgId);
        printedImage.setName(name);
        printedImage.setRfidTag(rfidTag);
        printedImage.setReusable(reusable);
        printedImage.setScanObjectType(scanObjectSupport.resolveType(orgId, scanObjectTypeId));
        printedImage.setImageUrl(imageUrl);
        PrintedImage saved = scanObjectRepository.save(printedImage);
        events.publishEvent(new CardsChangedEvent(orgId));
        return saved;
    }
}
