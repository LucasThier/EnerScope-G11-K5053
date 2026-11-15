package org.enerscope.version.service;

import lombok.RequiredArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.user.model.User;
import org.enerscope.util.AuthUtil;
import org.enerscope.version.dto.ProjectAccessDTO;
import org.enerscope.version.dto.VersionAccessDTO;
import org.enerscope.version.dto.VersionRoleGrantDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VersionAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final VersionMemberRepository versionMemberRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final AppLogger logger;

    @Transactional(readOnly = true)
    public ProjectAccessDTO resolveForProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        projectAccessGuard.assertCanViewProject(projectId);

        User caller = AuthUtil.requireSession().getUser();
        boolean platformAdmin = AuthUtil.isPlatformAdmin(caller);
        Optional<ProjectMember> projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .filter(ProjectAccessGuard::isActive);
        VersionAccess projectFallback = VersionAccess.fromProjectMembership(projectMember);
        ProjectMemberType projectRole = projectMember
                .flatMap(member -> member.getRoles().stream()
                        .filter(BaseEntity::isActive)
                        .map(ProjectMemberRole::getMemberType)
                        .findFirst())
                .orElse(null);
        List<ProjectMemberPermission> projectPermissions = projectMember
                .map(member -> Arrays.stream(ProjectMemberPermission.values())
                        .filter(permission -> ProjectAccessGuard.grants(member, permission))
                        .toList())
                .orElse(List.of());

        List<Version> versions = project.getVersions().stream()
                .filter(BaseEntity::isActive)
                .sorted(Comparator.comparing(Version::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<UUID, VersionMember> versionMembers = platformAdmin || versions.isEmpty()
                ? Map.of()
                : versionMemberRepository.findByUserIdAndVersionIdIn(caller.getId(),
                                versions.stream().map(Version::getId).toList()).stream()
                        .collect(Collectors.toMap(member -> member.getVersion().getId(), Function.identity()));

        List<VersionAccessDTO> versionAccess = versions.stream()
                .map(version -> toDTO(version, accessFor(platformAdmin, versionMembers.get(version.getId()), projectFallback),
                        projectRole))
                .toList();

        logger.debug("Resolved access of {} to {} versions of project {}", caller.getMail(), versionAccess.size(), projectId);
        return new ProjectAccessDTO(
                projectId,
                platformAdmin,
                projectRole,
                projectPermissions,
                platformAdmin || projectPermissions.contains(ProjectMemberPermission.MANAGE_PROJECT),
                platformAdmin || projectPermissions.contains(ProjectMemberPermission.MANAGE_PROJECT),
                versionAccess);
    }

    private VersionAccess accessFor(boolean platformAdmin, VersionMember versionMember, VersionAccess projectFallback) {
        if (platformAdmin) {
            return VersionAccess.platformAdmin();
        }
        if (projectFallback.can(VersionCapability.MANAGE)) {
            return VersionAccess.projectAdmin(versionMember == null ? List.of() : versionMember.getRoles());
        }
        if (versionMember != null) {
            return VersionAccess.fromVersionRoles(versionMember.getRoles());
        }
        return projectFallback;
    }

    private VersionAccessDTO toDTO(Version version, VersionAccess access, ProjectMemberType projectRole) {
        List<VersionRoleGrantDTO> roles = access.roles().stream()
                .sorted(Comparator.comparing(VersionMemberRole::getMemberType)
                        .thenComparing(role -> role.getVertical() == null ? "" : role.getVertical().name()))
                .map(role -> new VersionRoleGrantDTO(role.getMemberType(), role.getVertical()))
                .toList();
        boolean canView = access.can(VersionCapability.VIEW);
        boolean canEdit = access.can(VersionCapability.EDIT);
        return new VersionAccessDTO(
                version.getId(),
                version.getName(),
                access.source(),
                roles,
                effectiveRole(access, projectRole),
                access.editableVerticals(),
                canView,
                canEdit,
                access.can(VersionCapability.EDIT_CONNECTIONS),
                access.can(VersionCapability.SIMULATE),
                access.can(VersionCapability.MANAGE),
                access.canManageRoles(),
                canView,
                canView,
                canEdit);
    }

    private String effectiveRole(VersionAccess access, ProjectMemberType projectRole) {
        return switch (access.source()) {
            case PLATFORM_ADMIN, PROJECT_ADMIN -> VersionMemberType.ADMIN.name();
            case VERSION_ROLES -> access.roles().stream()
                    .map(VersionMemberRole::getMemberType)
                    .min(Comparator.naturalOrder())
                    .map(Enum::name)
                    .orElse(null);
            case PROJECT_FALLBACK -> projectRole == null ? null : projectRole.name();
        };
    }
}
