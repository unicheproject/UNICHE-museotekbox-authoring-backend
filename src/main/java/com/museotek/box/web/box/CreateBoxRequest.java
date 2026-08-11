package com.museotek.box.web.box;

import jakarta.validation.constraints.NotBlank;

public record CreateBoxRequest(
        @NotBlank String name,
        @NotBlank String serialNumber
) {}
