package com.museotek.box.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.domain.template.InvalidTemplateException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** The checks for what a manager sends when creating or changing a template. */
final class ExperienceTemplateRules {

    static final int MAX_NAME_LENGTH = 255;
    static final int MAX_DESCRIPTION_LENGTH = 1000;
    static final int MAX_DEFAULTS_BYTES = 64 * 1024;

    private ExperienceTemplateRules() {
    }

    // A plain string rather than the enum in the request, so an unknown type is a clear 400 INVALID_TEMPLATE.
    static ExperienceTemplateType requireType(String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidTemplateException("The type is required");
        }
        try {
            return ExperienceTemplateType.valueOf(type.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidTemplateException("Unknown type '" + type + "' (expected one of "
                    + Arrays.toString(ExperienceTemplateType.values()) + ")");
        }
    }

    static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidTemplateException("The name can't be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidTemplateException("The name may be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    // Blank means no description.
    static String description(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTemplateException("The description may be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    // Only the outer shape is checked: what's inside belongs to the frontend's form for this type.
    static String requireDefaults(JsonNode defaults) {
        if (!defaults.isObject()) {
            throw new InvalidTemplateException("defaults must be a JSON object");
        }
        String json = defaults.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DEFAULTS_BYTES) {
            throw new InvalidTemplateException("defaults may be at most " + MAX_DEFAULTS_BYTES / 1024 + " KB");
        }
        return json;
    }

    // Left out of the request, or sent as null.
    static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }
}
