package com.museotek.box.web.scanobjecttype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// universal / exit: absent = unchanged.
public record UpdateScanObjectTypeRequest(@NotBlank @Size(max = 255) String name, Boolean universal, Boolean exit) {}
