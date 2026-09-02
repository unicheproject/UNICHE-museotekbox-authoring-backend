package com.museotek.box.web.scanobject;

import jakarta.validation.constraints.NotBlank;

public record UpdatePrintedImageRequest(
        @NotBlank String name,
        String rfidTag,
        boolean reusable,
        Long scanObjectTypeId,
        @NotBlank String imageUrl
) {}
