package org.enerscope.project.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.project.model.enums.ProjectMemberType;

import java.util.UUID;

public record AddProjectMemberRequestDTO(
        @NotNull
        UUID userId,

        @NotNull
        ProjectMemberType memberType
) {}
