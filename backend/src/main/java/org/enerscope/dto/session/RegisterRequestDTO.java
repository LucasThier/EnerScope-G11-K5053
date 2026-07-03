package org.enerscope.dto.session;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank @Email
        String mail,

        @NotBlank @Size(min = 2, max = 60)
        String firstName,

        @NotBlank @Size(min = 2, max = 60)
        String lastName,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long")
        String password
) {}
