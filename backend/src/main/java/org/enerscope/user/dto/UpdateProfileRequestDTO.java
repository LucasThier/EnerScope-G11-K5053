package org.enerscope.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(
        @Size(min = 2, max = 60)
        String firstName,

        @Size(min = 2, max = 60)
        String lastName,

        @Size(max = 120)
        String jobTitle
) {}
