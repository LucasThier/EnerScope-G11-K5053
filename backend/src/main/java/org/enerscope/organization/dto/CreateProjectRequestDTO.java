package org.enerscope.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequestDTO(
        @NotBlank @Size(min = 2, max = 120)
        String name,

        @NotBlank @Size(max = 500)
        String description
) {}
