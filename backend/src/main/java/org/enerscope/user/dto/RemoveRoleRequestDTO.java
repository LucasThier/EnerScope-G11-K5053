package org.enerscope.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Removes a user's role at any level. {@code resourceId} is required for
 * {@link RoleLevel#ORGANIZATION}/{@link RoleLevel#PROJECT} and must be
 * omitted for {@link RoleLevel#PLATFORM} — the platform role cannot be
 * removed, only reassigned via {@code AssignRoleRequestDTO}.
 */
public record RemoveRoleRequestDTO(
        @NotNull
        RoleLevel level,

        UUID resourceId
) {}
