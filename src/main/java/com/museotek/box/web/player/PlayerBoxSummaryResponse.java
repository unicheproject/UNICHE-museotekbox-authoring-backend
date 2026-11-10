package com.museotek.box.web.player;

import com.museotek.box.domain.box.Box;

/** A Box in a screen's picker. No serial number: it is private and the screen doesn't need it. */
public record PlayerBoxSummaryResponse(Long id, String name) {

    public static PlayerBoxSummaryResponse from(Box box) {
        return new PlayerBoxSummaryResponse(box.getId(), box.getName());
    }
}
