package org.enerscope.user.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.user.model.enums.PlatformRole;

public record UpdateRoleRequestDTO(
        @NotNull
        PlatformRole platformRole
) {}
