package org.enerscope.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Assigns a role to a user at any level. {@code resourceId} is required for
 * {@link RoleLevel#ORGANIZATION}/{@link RoleLevel#PROJECT} (the org/project
 * id) and must be omitted for {@link RoleLevel#PLATFORM}. {@code roleType} is
 * the enum constant name for that level (e.g. {@code OWNER}/{@code MEMBER}
 * for organizations, {@code ADMIN}/{@code EDITOR} for projects, {@code
 * ADMIN}/{@code USER} for the platform).
 */
public record AssignRoleRequestDTO(
        @NotNull
        RoleLevel level,

        UUID resourceId,

        @NotBlank
        String roleType
) {}
