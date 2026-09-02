package com.museotek.box.web.scanobject;

import com.museotek.box.domain.scanobject.CardColour;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateColouredCardRequest(
        @NotBlank String name,
        String rfidTag,
        boolean reusable,
        Long scanObjectTypeId,
        @NotNull CardColour colour
) {}
