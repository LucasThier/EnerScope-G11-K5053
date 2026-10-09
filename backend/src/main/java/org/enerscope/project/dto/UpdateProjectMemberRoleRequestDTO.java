package org.enerscope.project.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.project.model.enums.ProjectMemberType;

public record UpdateProjectMemberRoleRequestDTO(
        @NotNull
        ProjectMemberType memberType
) {}
