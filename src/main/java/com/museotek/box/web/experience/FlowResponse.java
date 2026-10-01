package com.museotek.box.web.experience;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.museotek.box.domain.experience.ExperienceFlow;

// content is embedded raw, like BlockResponse.content: it is a jsonb column, so it is already valid JSON.
public record FlowResponse(Integer schemaVersion, @JsonRawValue String content) {

    // null in, null out: a project has no flow until its experience is first saved.
    public static FlowResponse from(ExperienceFlow flow) {
        if (flow == null) {
            return null;
        }
        return new FlowResponse(flow.getSchemaVersion(), flow.getFlow());
    }
}
