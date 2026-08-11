package com.museotek.box.web.box;

import com.museotek.box.domain.box.Box;

import java.util.UUID;

public record BoxResponse(Long id, UUID orgId, String name, String serialNumber, String status, UUID currentProjectId) {

    public static BoxResponse from(Box box) {
        UUID currentProjectId = box.getCurrentProject() != null ? box.getCurrentProject().getId() : null;
        return new BoxResponse(box.getId(), box.getOrgId(), box.getName(), box.getSerialNumber(),
                box.getStatus().name(), currentProjectId);
    }
}
