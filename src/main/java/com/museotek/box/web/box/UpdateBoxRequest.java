package com.museotek.box.web.box;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBoxRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String serialNumber
) {}
