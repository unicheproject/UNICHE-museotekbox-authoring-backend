package com.museotek.box.web.scanobject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDraftRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String rfidTag,
        boolean reusable,
        Long scanObjectTypeId
) {}
