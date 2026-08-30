package org.enerscope.version.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVersionRequestDTO(
        @NotBlank @Size(min = 2, max = 120)
        String name,

        UUID parentVersionId
) {}
