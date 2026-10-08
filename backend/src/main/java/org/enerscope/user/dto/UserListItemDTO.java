package org.enerscope.user.dto;

import org.enerscope.user.model.enums.PlatformRole;

import java.util.UUID;

public record UserListItemDTO(
        UUID id,
        String mail,
        String firstName,
        String lastName,
        String jobTitle,
        PlatformRole platformRole,
        boolean active,
        long organizationCount
) {}
