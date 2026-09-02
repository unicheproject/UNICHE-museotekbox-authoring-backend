package com.museotek.box.web.scanobject;

import jakarta.validation.constraints.NotBlank;

public record CreateDraftRequest(
        @NotBlank String name,
        String rfidTag,
        boolean reusable,
        Long scanObjectTypeId
) {}
