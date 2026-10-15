package org.enerscope.role.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.role.dto.RoleLevel;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Hierarchy check for assigning/removing a role: a caller may only grant or
 * revoke a role whose level is at or below their own authority at that
 * resource. Rank is PLATFORM &gt; ORGANIZATION &gt; PROJECT, and a level's
 * authority covers everything below it in scope — an organization OWNER may
 * manage any project inside their organization without needing an explicit
 * membership there; a project ADMIN only manages that one project.
 */
@Service
public class RoleAuthorizationService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public RoleAuthorizationService(OrganizationMemberRepository organizationMemberRepository,
                                     ProjectRepository projectRepository,
                                     ProjectMemberRepository projectMemberRepository) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    public void assertCanAssign(RoleLevel level, UUID resourceId) {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User caller = session.getUser();
        if (caller.getPlatformRole() == PlatformRole.ADMIN) {
            return;
        }

        boolean allowed = switch (level) {
            // Only a platform ADMIN may ever assign a platform-level role.
            case PLATFORM -> false;
            case ORGANIZATION -> isOrganizationOwner(resourceId, caller.getId());
            case PROJECT -> isProjectAdmin(resourceId, caller.getId())
                    || isOrganizationOwner(ownerOrganizationId(resourceId), caller.getId());
        };

        if (!allowed) {
            throw new ForbiddenException("You are not allowed to assign roles at this level");
        }
    }

    private boolean isOrganizationOwner(UUID organizationId, UUID userId) {
        if (organizationId == null) {
            return false;
        }
        return organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(member -> member.getRoles().stream()
                        .anyMatch(role -> role.getPermissions().contains(OrganizationMemberPermission.MANAGE_ORGANIZATION)))
                .orElse(false);
    }

    private boolean isProjectAdmin(UUID projectId, UUID userId) {
        if (projectId == null) {
            return false;
        }
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(member -> member.getRoles().stream()
                        .anyMatch(role -> role.getPermissions().contains(ProjectMemberPermission.MANAGE_PROJECT)))
                .orElse(false);
    }

    private UUID ownerOrganizationId(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findById(projectId)
                .map(Project::getOrganization)
                .map(org -> org.getId())
                .orElse(null);
    }
}
