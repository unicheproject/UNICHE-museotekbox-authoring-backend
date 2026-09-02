package com.museotek.box.application.scanobject;

import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared by every ScanObject create/update use case: RFID-uniqueness and ScanObjectType-existence
 * checks are identical across all four subtypes, so they live here instead of being repeated per use case.
 */
@Component
public class ScanObjectSupport {

    private final ScanObjectRepository scanObjectRepository;
    private final ScanObjectTypeRepository scanObjectTypeRepository;

    public ScanObjectSupport(ScanObjectRepository scanObjectRepository, ScanObjectTypeRepository scanObjectTypeRepository) {
        this.scanObjectRepository = scanObjectRepository;
        this.scanObjectTypeRepository = scanObjectTypeRepository;
    }

    // rfidTag is nullable (a Draft may have none); excludeId is null on create so nothing is ever excluded.
    public void ensureRfidTagAvailable(String rfidTag, Long excludeId) {
        if (rfidTag == null) {
            return;
        }
        scanObjectRepository.findByRfidTag(rfidTag)
                .filter(other -> !other.getId().equals(excludeId))
                .ifPresent(other -> {
                    throw new DuplicateRfidTagException("A scan object with RFID tag " + rfidTag + " already exists");
                });
    }

    public ScanObjectType resolveType(UUID orgId, Long scanObjectTypeId) {
        if (scanObjectTypeId == null) {
            return null;
        }
        return scanObjectTypeRepository.findByIdAndOrgId(scanObjectTypeId, orgId)
                .orElseThrow(() -> new ScanObjectTypeNotFoundException("No scan object type " + scanObjectTypeId + " for org " + orgId));
    }
}
