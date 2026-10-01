package com.museotek.box.web.experience;

import com.museotek.box.domain.experience.ExperienceVariable;

public record VariableResponse(String key, String kind, String initial) {

    public static VariableResponse from(ExperienceVariable variable) {
        return new VariableResponse(variable.getVariableKey(), variable.getKind().name(), variable.getInitialValue());
    }
}
