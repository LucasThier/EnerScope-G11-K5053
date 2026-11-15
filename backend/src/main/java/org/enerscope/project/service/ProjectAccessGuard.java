package org.enerscope.project.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.user.model.User;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Authorization rules for projects. The version-scoped equivalents
 * ({@code assertCanViewVersion}, {@code assertCanEditVersion}, etc.) live on
 * {@code version.service.VersionAccessGuard} instead of here: every endpoint
 * that needs them goes straight through the version package, and that guard
 * resolves a version's owning project and falls back to this one's checks.
 *
 * <p>Every method follows the shape of
 * {@code OrganizationService.assertCanManageUsers}: no session is a
 * {@link UnauthorizedException} (401), a platform {@code ADMIN} passes without
 * any membership lookup, and everyone else needs the matching permission on the
 * project, or gets a {@link ForbiddenException} (403).</p>
 */
@Component
public class ProjectAccessGuard {

    private final ProjectMemberRepository projectMemberRepository;
    private final AppLogger logger;

    public ProjectAccessGuard(ProjectMemberRepository projectMemberRepository,
                              AppLogger logger) {
        this.projectMemberRepository = projectMemberRepository;
        this.logger = logger;
    }

    /**
     * Ensures the caller may read the project: a platform ADMIN, or any of its
     * members regardless of permissions. Reading who has access is deliberately
     * more permissive than any management action, mirroring
     * {@code OrganizationService.assertCanViewOrganization}.
     */
    public void assertCanViewProject(UUID projectId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        boolean member = projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .filter(ProjectAccessGuard::isActive)
                .isPresent();
        if (!member) {
            deny(caller, "view", projectId);
        }
    }

    public boolean canManageProject(UUID projectId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return true;
        }
        return projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .map(member -> grants(member, ProjectMemberPermission.MANAGE_PROJECT))
                .orElse(false);
    }

    /**
     * Ensures the caller may change the project's contents — its versions, and
     * the nodes and connections inside them. Requires
     * {@link ProjectMemberPermission#EDIT_PROJECT}, which both ADMIN and EDITOR
     * members hold.
     */
    public void assertCanEditProject(UUID projectId) {
        assertHasPermission(projectId, ProjectMemberPermission.EDIT_PROJECT, "edit");
    }

    /**
     * Ensures the caller may administer the project itself — changing who is on
     * it, creating its root versions, and governing the roles of its versions.
     * Requires {@link ProjectMemberPermission#MANAGE_PROJECT}, which only ADMIN
     * members hold; a platform ADMIN passes without it.
     */
    public void assertCanManageProject(UUID projectId) {
        assertHasPermission(projectId, ProjectMemberPermission.MANAGE_PROJECT, "manage");
    }

    private void assertHasPermission(UUID projectId, ProjectMemberPermission permission, String action) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        boolean allowed = projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .map(member -> grants(member, permission))
                .orElse(false);
        if (!allowed) {
            deny(caller, action, projectId);
        }
    }

    public static boolean isActive(ProjectMember member) {
        return member.isActive() && member.getUser().isActive();
    }

    public static boolean grants(ProjectMember member, ProjectMemberPermission permission) {
        return isActive(member) && member.getRoles().stream()
                .anyMatch(role -> role.isActive() && role.getPermissions().contains(permission));
    }

    private void deny(User caller, String action, UUID projectId) {
        logger.warn("User {} is not allowed to {} project {}", caller.getMail(), action, projectId);
        throw new ForbiddenException("You are not allowed to " + action + " this project");
    }
}
