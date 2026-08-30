package org.enerscope.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProjectRequestDTO(
        @NotBlank @Size(min = 2, max = 120)
        String name,

        @NotBlank @Size(max = 500)
        String description,

        @NotNull
        UUID organizationId
) {}
