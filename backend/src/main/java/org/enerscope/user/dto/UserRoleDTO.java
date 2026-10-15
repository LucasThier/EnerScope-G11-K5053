package org.enerscope.user.dto;

import java.util.UUID;

/**
 * One role a user holds, at any level. {@code resourceId}/{@code
 * resourceName} are null for a {@link RoleLevel#PLATFORM} entry, since the
 * platform role has no resource of its own.
 */
public record UserRoleDTO(
        RoleLevel level,
        UUID resourceId,
        String resourceName,
        String roleType
) {}
