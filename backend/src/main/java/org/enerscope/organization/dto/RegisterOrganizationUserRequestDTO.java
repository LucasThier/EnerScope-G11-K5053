package org.enerscope.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for an organization owner (or a platform admin) to register a brand
 * new user directly into an organization. The account is always created as a
 * regular platform user and added to the organization as a MEMBER.
 */
public record RegisterOrganizationUserRequestDTO(
        @NotBlank @Email
        String mail,

        @NotBlank @Size(min = 2, max = 60)
        String firstName,

        @NotBlank @Size(min = 2, max = 60)
        String lastName,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long")
        String password
) {}
