package com.museotek.box.web.scanobject;

import jakarta.validation.constraints.NotBlank;

public record UpdateDraftRequest(
        @NotBlank String name,
        String rfidTag,
        boolean reusable,
        Long scanObjectTypeId
) {}
