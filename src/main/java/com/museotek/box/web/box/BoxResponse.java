package com.museotek.box.web.box;

import com.museotek.box.domain.box.Box;

import java.time.Instant;
import java.util.UUID;

// status, currentProjectId, currentSceneKey and lastSeenAt are what the player project last reported.
// hasKey and keyCreatedAt say whether the Box can log in; the key itself is never returned here.
public record BoxResponse(Long id, UUID orgId, String name, String serialNumber, String status, UUID currentProjectId,
                          String currentSceneKey, Instant lastSeenAt, boolean hasKey, Instant keyCreatedAt) {

    public static BoxResponse from(Box box) {
        UUID currentProjectId = box.getCurrentProject() != null ? box.getCurrentProject().getId() : null;
        return new BoxResponse(box.getId(), box.getOrgId(), box.getName(), box.getSerialNumber(),
                box.getStatus().name(), currentProjectId, box.getCurrentSceneKey(), box.getLastSeenAt(),
                box.getKeyHash() != null, box.getKeyCreatedAt());
    }
}
