package org.enerscope.project.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared project-authorization checks. Extracted out of {@link ProjectService}
 * so a version's own controller can gate its endpoints on the owning
 * project's membership without introducing a circular dependency
 * ({@code ProjectService} already depends on {@code VersionService}).
 */
@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public ProjectAccessService(ProjectRepository projectRepository,
                                 ProjectMemberRepository projectMemberRepository,
                                 OrganizationMemberRepository organizationMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    /** A platform ADMIN, an org OWNER of the owning organization, or any project member. */
    public void assertCanView(UUID projectId) {
        if (!hasAuthority(projectId, null)) {
            throw new ForbiddenException("You are not allowed to view this project");
        }
    }

    /** As {@link #assertCanView}, but a plain project member also needs EDIT_PROJECT. */
    public void assertCanEdit(UUID projectId) {
        if (!hasAuthority(projectId, ProjectMemberPermission.EDIT_PROJECT)) {
            throw new ForbiddenException("You are not allowed to edit this project");
        }
    }

    /** As {@link #assertCanView}, but a plain project member also needs MANAGE_PROJECT. */
    public void assertCanManage(UUID projectId) {
        if (!hasAuthority(projectId, ProjectMemberPermission.MANAGE_PROJECT)) {
            throw new ForbiddenException("You are not allowed to manage this project");
        }
    }

    /**
     * The project that owns a version. {@code Version} carries no back-reference
     * of its own; the relationship is only navigable from {@code Project.versions}.
     */
    public UUID resolveProjectIdForVersion(UUID versionId) {
        return projectRepository.findByVersions_Id(versionId)
                .map(Project::getId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
    }

    /**
     * {@code requiredPermission == null} means "any member is enough" (view-only).
     * Platform ADMIN and an org OWNER of the owning organization always pass,
     * regardless of the permission requested, per the "level implies full
     * scope" authority rule.
     */
    private boolean hasAuthority(UUID projectId, ProjectMemberPermission requiredPermission) {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User caller = session.getUser();
        if (caller.getPlatformRole() == PlatformRole.ADMIN) {
            return true;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        boolean organizationOwner = organizationMemberRepository
                .findByOrganizationIdAndUserId(project.getOrganization().getId(), caller.getId())
                .map(member -> member.getRoles().stream()
                        .anyMatch(role -> role.getPermissions().contains(OrganizationMemberPermission.MANAGE_ORGANIZATION)))
                .orElse(false);
        if (organizationOwner) {
            return true;
        }

        if (requiredPermission == null) {
            return projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId());
        }

        return projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .map(member -> member.getRoles().stream()
                        .anyMatch(role -> role.getPermissions().contains(requiredPermission)))
                .orElse(false);
    }
}
