package org.enerscope.user.dto;

import org.enerscope.user.model.enums.PlatformRole;

import java.util.List;
import java.util.UUID;

/**
 * Full read-only record behind {@code GET /users/{id}}: identity plus every
 * organization and project the user belongs to. Platform-admin only; a
 * suspended account still answers with its detail, since that is exactly the
 * account an admin needs to inspect.
 */
public record UserDetailDTO(
        UUID id,
        String mail,
        String firstName,
        String lastName,
        String jobTitle,
        PlatformRole platformRole,
        boolean active,
        List<UserOrganizationMembershipDTO> organizations,
        List<UserProjectMembershipDTO> projects
) {}
