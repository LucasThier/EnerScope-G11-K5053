package org.enerscope.version.dto;

import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;

import java.util.List;
import java.util.UUID;

public record ProjectAccessDTO(
        UUID projectId,
        boolean platformAdmin,
        ProjectMemberType projectRole,
        List<ProjectMemberPermission> projectPermissions,
        boolean canManageProject,
        boolean canCreateRootVersions,
        List<VersionAccessDTO> versions
) {}
