package com.museotek.box.web.project;

import jakarta.validation.constraints.NotBlank;

public record UpdateProjectRequest(@NotBlank String name) {}
