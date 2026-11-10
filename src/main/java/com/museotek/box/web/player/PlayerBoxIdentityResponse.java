package com.museotek.box.web.player;

import com.museotek.box.domain.box.Box;

import java.util.UUID;

public record PlayerBoxIdentityResponse(Long id, UUID orgId, String name) {

    public static PlayerBoxIdentityResponse from(Box box) {
        return new PlayerBoxIdentityResponse(box.getId(), box.getOrgId(), box.getName());
    }
}
