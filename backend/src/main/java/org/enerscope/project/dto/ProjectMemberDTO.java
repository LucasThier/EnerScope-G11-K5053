package org.enerscope.project.dto;

import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;

import java.util.Set;
import java.util.UUID;

public record ProjectMemberDTO(
        UUID id,
        UUID userId,
        String userMail,
        ProjectMemberType memberType,
        Set<ProjectMemberPermission> permissions
) {}
