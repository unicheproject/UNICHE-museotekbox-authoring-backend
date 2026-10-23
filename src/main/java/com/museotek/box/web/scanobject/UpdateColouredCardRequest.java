package com.museotek.box.web.scanobject;

import com.museotek.box.domain.scanobject.CardColour;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateColouredCardRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String rfidTag,
        boolean reusable,
        Long scanObjectTypeId,
        @NotNull CardColour colour
) {}
