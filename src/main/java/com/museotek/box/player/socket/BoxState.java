package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.museotek.box.application.player.DisplayMediaLinks;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Box's {@code state} message: a full snapshot of what its display should show. The tool checks only its
 * {@code mode}, keeps the latest one per Box, and passes it on to the display unchanged apart from {@code mediaLinks}.
 */
final class BoxState {

    static final Set<String> MODES = Set.of("IDLE", "PLAYING", "PLAYING_ON_BOX");

    /** What the Box is playing, for the boxes table: nothing when idle. */
    record Playing(UUID projectId, String sceneKey) {
        static final Playing NOTHING = new Playing(null, null);
    }

    private BoxState() {
    }

    /** True if the payload is an object with a known {@code mode}. Everything else in it is the Box's business. */
    static boolean isValid(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        JsonNode mode = payload.get("mode");
        return mode != null && mode.isTextual() && MODES.contains(mode.asText());
    }

    static Playing playing(JsonNode state) {
        if (state == null || "IDLE".equals(state.get("mode").asText())) {
            return Playing.NOTHING;
        }
        UUID projectId = uuid(state.path("session").path("projectId"));
        JsonNode sceneKey = state.path("scene").path("sceneKey");
        return new Playing(projectId, sceneKey.isTextual() ? sceneKey.asText() : null);
    }

    /**
     * The snapshot as this display gets it: the same, plus {@code mediaLinks}, a signed link per {@code mediaId} found
     * anywhere in it (blocks, overlay). A {@code <video>} or {@code <img>} can't send the display token, so it loads
     * these links instead.
     */
    static ObjectNode forDisplay(JsonNode state, UUID displaySessionId, DisplayMediaLinks links) {
        ObjectNode copy = state.deepCopy();
        Set<UUID> mediaIds = new LinkedHashSet<>();
        collectMediaIds(state, mediaIds);
        ObjectNode mediaLinks = copy.putObject("mediaLinks");
        for (UUID mediaId : mediaIds) {
            mediaLinks.put(mediaId.toString(), links.link(displaySessionId, mediaId));
        }
        return copy;
    }

    private static void collectMediaIds(JsonNode node, Set<UUID> mediaIds) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if ("mediaId".equals(field.getKey())) {
                    UUID id = uuid(field.getValue());
                    if (id != null) {
                        mediaIds.add(id);
                    }
                } else {
                    collectMediaIds(field.getValue(), mediaIds);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectMediaIds(item, mediaIds);
            }
        }
    }

    private static UUID uuid(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
