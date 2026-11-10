package com.museotek.box.web.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** In a body, not the URL: the serial number is private and URLs end up in access logs. */
public record IdentifyBoxRequest(@NotBlank @Size(max = 255) String serialNumber) {
}
