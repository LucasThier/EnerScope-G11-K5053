package org.enerscope.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body shape shared by the assign and remove endpoints. {@code resourceId}
 * is required for {@code ORGANIZATION}/{@code PROJECT} and ignored for
 * {@code PLATFORM}. {@code roleType} must match the enum value of the target
 * level ({@code PlatformRole}, {@code OrganizationMemberType} or
 * {@code ProjectMemberType}).
 */
public record RoleAssignmentRequestDTO(
        @NotNull
        RoleLevel level,

        UUID resourceId,

        @NotBlank
        String roleType
) {}
