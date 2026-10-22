package com.museotek.box.web.template;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.museotek.box.domain.template.ExperienceTemplate;

import java.time.Instant;
import java.util.UUID;

// defaults is stored as JSON text and sent back as the JSON object it is, not as a string.
public record ExperienceTemplateResponse(
        UUID id,
        String type,
        String name,
        String description,
        @JsonRawValue String defaults,
        boolean global,
        Instant createdAt,
        Instant updatedAt
) {

    public static ExperienceTemplateResponse from(ExperienceTemplate template) {
        return new ExperienceTemplateResponse(template.getId(), template.getType().name(), template.getName(),
                template.getDescription(), template.getDefaults(), template.isGlobal(),
                template.getCreatedAt(), template.getUpdatedAt());
    }
}
