package org.enerscope.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /users/me/password}. The account is always the caller's
 * own — it is never carried in the body — so the only thing to send is proof of
 * the current password plus the replacement.
 */
public record ChangePasswordRequestDTO(
        /**
         * The caller's current password. Not length-validated on purpose: it is
         * checked against the stored hash, and rejecting it for being too short
         * would leak that the stored one is short.
         */
        @NotBlank
        String currentPassword,

        /** The replacement, held to the same minimum as {@code RegisterRequestDTO}. */
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long")
        String newPassword
) {}
