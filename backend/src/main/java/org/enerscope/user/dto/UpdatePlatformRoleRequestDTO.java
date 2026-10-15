package org.enerscope.user.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.user.model.enums.PlatformRole;

public record UpdatePlatformRoleRequestDTO(
        @NotNull
        PlatformRole role
) {}
