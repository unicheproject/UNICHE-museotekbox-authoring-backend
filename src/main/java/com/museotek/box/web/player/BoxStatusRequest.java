package com.museotek.box.web.player;

import com.museotek.box.application.player.BoxStatusReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** {@code runningProjectId} and {@code sceneKey} are optional: null = the Box is idle. */
public record BoxStatusRequest(@NotNull Boolean online, UUID runningProjectId, @Size(max = 255) String sceneKey,
                               @NotNull Instant at) {

    public BoxStatusReport toReport() {
        return new BoxStatusReport(online, runningProjectId, sceneKey, at);
    }
}
