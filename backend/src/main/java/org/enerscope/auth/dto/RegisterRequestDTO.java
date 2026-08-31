package org.enerscope.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.enerscope.user.model.enums.PlatformRole;

public record RegisterRequestDTO(
        @NotBlank @Email
        String mail,

        @NotBlank @Size(min = 2, max = 60)
        String firstName,

        @NotBlank @Size(min = 2, max = 60)
        String lastName,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long")
        String password,

        /**
         * Optional platform role for the new account. When omitted the user is
         * created as a regular {@link PlatformRole#USER}. Only a platform ADMIN
         * can reach this endpoint, so allowing ADMIN here lets admins mint other
         * admins.
         */
        PlatformRole role
) {}
