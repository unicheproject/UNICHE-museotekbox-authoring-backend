package com.museotek.box.application.experience;

import java.util.List;

public record ExperienceValidationResult(List<String> errors) {

    public boolean isValid() {
        return errors.isEmpty();
    }
}
