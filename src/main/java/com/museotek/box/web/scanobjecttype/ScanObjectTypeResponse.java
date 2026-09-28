package com.museotek.box.web.scanobjecttype;

import com.museotek.box.domain.scanobject.ScanObjectType;

import java.util.UUID;

public record ScanObjectTypeResponse(Long id, UUID orgId, String name) {

    public static ScanObjectTypeResponse from(ScanObjectType type) {
        return new ScanObjectTypeResponse(type.getId(), type.getOrgId(), type.getName());
    }
}
