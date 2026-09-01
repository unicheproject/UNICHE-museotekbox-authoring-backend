package com.museotek.box.web.box;

import jakarta.validation.constraints.NotBlank;

public record UpdateBoxRequest(
        @NotBlank String name,
        @NotBlank String serialNumber
) {}
