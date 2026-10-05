package org.enerscope.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequestDTO(
        @NotBlank @Size(min = 2, max = 120)
        String name
) {}
