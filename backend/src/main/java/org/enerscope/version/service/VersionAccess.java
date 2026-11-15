package org.enerscope.version.service;

import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionAccessSource;
import org.enerscope.version.model.enums.VersionMemberType;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class VersionAccess {

    private final VersionAccessSource source;
    private final List<VersionMemberRole> roles;
    private final Set<ProjectMemberPermission> projectPermissions;
    private final boolean projectMember;

    private VersionAccess(VersionAccessSource source, List<VersionMemberRole> roles,
                          Set<ProjectMemberPermission> projectPermissions, boolean projectMember) {
        this.source = source;
        this.roles = roles;
        this.projectPermissions = projectPermissions;
        this.projectMember = projectMember;
    }

    public static VersionAccess platformAdmin() {
        return new VersionAccess(VersionAccessSource.PLATFORM_ADMIN, List.of(), Set.of(), true);
    }

    public static VersionAccess fromVersionRoles(Collection<VersionMemberRole> roles) {
        return new VersionAccess(VersionAccessSource.VERSION_ROLES, List.copyOf(roles), Set.of(), false);
    }

    public static VersionAccess projectAdmin(Collection<VersionMemberRole> roles) {
        return new VersionAccess(VersionAccessSource.PROJECT_ADMIN, List.copyOf(roles), Set.of(), true);
    }

    public static VersionAccess fromProjectMembership(Optional<ProjectMember> member) {
        Optional<ProjectMember> active = member.filter(ProjectAccessGuard::isActive);
        Set<ProjectMemberPermission> permissions = EnumSet.noneOf(ProjectMemberPermission.class);
        active.ifPresent(m -> {
            for (ProjectMemberPermission permission : ProjectMemberPermission.values()) {
                if (ProjectAccessGuard.grants(m, permission)) {
                    permissions.add(permission);
                }
            }
        });
        return new VersionAccess(VersionAccessSource.PROJECT_FALLBACK, List.of(), permissions, active.isPresent());
    }

    public VersionAccessSource source() {
        return source;
    }

    public List<VersionMemberRole> roles() {
        return roles;
    }

    public boolean can(VersionCapability capability) {
        return switch (source) {
            case PLATFORM_ADMIN, PROJECT_ADMIN -> true;
            case VERSION_ROLES -> grantedByRoles(capability);
            case PROJECT_FALLBACK -> grantedByProject(capability);
        };
    }

    public boolean canEditVertical(VerticalEnum vertical) {
        return switch (source) {
            case PLATFORM_ADMIN, PROJECT_ADMIN -> true;
            case VERSION_ROLES -> roles.stream().anyMatch(role -> VersionAccessGuard.canEditVertical(role, vertical));
            case PROJECT_FALLBACK -> grantedByProject(VersionCapability.EDIT);
        };
    }

    public List<VerticalEnum> editableVerticals() {
        return Arrays.stream(VerticalEnum.values()).filter(this::canEditVertical).toList();
    }

    private boolean grantedByRoles(VersionCapability capability) {
        if (capability == VersionCapability.VIEW) {
            return true;
        }
        if (capability == VersionCapability.SIMULATE
                && roles.stream().anyMatch(role -> role.getMemberType() == VersionMemberType.EDITOR)) {
            return true;
        }
        return VersionAccessGuard.carries(roles, capability.versionPermission());
    }

    private boolean grantedByProject(VersionCapability capability) {
        if (capability.projectPermission() == null) {
            return projectMember;
        }
        return projectPermissions.contains(capability.projectPermission());
    }
}
