package com.museotek.box.web.scanobjecttype;

import jakarta.validation.constraints.NotBlank;

public record UpdateScanObjectTypeRequest(@NotBlank String name) {}
