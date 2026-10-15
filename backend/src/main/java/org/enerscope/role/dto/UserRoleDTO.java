package org.enerscope.role.dto;

import java.util.Set;
import java.util.UUID;

/**
 * One role a user holds. {@code resourceId}/{@code resourceName} are null for
 * {@code PLATFORM} (there is no resource to scope it to).
 */
public record UserRoleDTO(
        RoleLevel level,
        UUID resourceId,
        String resourceName,
        String roleName,
        Set<String> permissions
) {}
