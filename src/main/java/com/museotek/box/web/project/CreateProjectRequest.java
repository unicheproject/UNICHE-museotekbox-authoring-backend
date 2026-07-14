package com.museotek.box.web.project;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String slug,
        @NotBlank String toolSlug
) {}
