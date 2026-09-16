package org.enerscope.project.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Authorization rules for projects and for the versions that hang off them.
 *
 * <p>These checks live in their own component instead of on
 * {@link ProjectService} — where the organization side keeps its equivalents —
 * because {@code VersionService} needs the same rules and {@code ProjectService}
 * already depends on {@code VersionService}. Asking the service for the check
 * would close that loop into a circular dependency; depending only on
 * repositories keeps the guard usable from both sides.</p>
 *
 * <p>Every method follows the shape of
 * {@code OrganizationService.assertCanManageUsers}: no session is a
 * {@link UnauthorizedException} (401), a platform {@code ADMIN} passes without
 * any membership lookup, and everyone else needs the matching permission on the
 * project, or gets a {@link ForbiddenException} (403).</p>
 */
@Component
public class ProjectAccessGuard {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final AppLogger logger;

    public ProjectAccessGuard(ProjectRepository projectRepository,
                              ProjectMemberRepository projectMemberRepository,
                              AppLogger logger) {
        this.projectRepository = projectRepository;
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
        User caller = requireSession().getUser();
        if (isPlatformAdmin(caller)) {
            return;
        }
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())) {
            deny(caller, "view", projectId);
        }
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
     * Ensures the caller may administer the project itself — today, changing who
     * is on it. Requires {@link ProjectMemberPermission#MANAGE_PROJECT}, which
     * only ADMIN members hold.
     */
    public void assertCanManageProject(UUID projectId) {
        assertHasPermission(projectId, ProjectMemberPermission.MANAGE_PROJECT, "manage");
    }

    /** As {@link #assertCanViewProject}, resolved through the version's owning project. */
    public void assertCanViewVersion(UUID versionId) {
        User caller = requireSession().getUser();
        if (isPlatformAdmin(caller)) {
            return;
        }
        assertCanViewProject(owningProjectId(versionId));
    }

    /** As {@link #assertCanEditProject}, resolved through the version's owning project. */
    public void assertCanEditVersion(UUID versionId) {
        User caller = requireSession().getUser();
        if (isPlatformAdmin(caller)) {
            return;
        }
        assertCanEditProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller is a platform ADMIN, for actions that no project or
     * organization owns — currently creating a version detached from every
     * project.
     */
    public void assertIsPlatformAdmin(String action) {
        User caller = requireSession().getUser();
        if (!isPlatformAdmin(caller)) {
            logger.warn("User {} is not a platform admin and may not {}", caller.getMail(), action);
            throw new ForbiddenException("Only platform admins can " + action);
        }
    }

    private void assertHasPermission(UUID projectId, ProjectMemberPermission permission, String action) {
        User caller = requireSession().getUser();
        if (isPlatformAdmin(caller)) {
            return;
        }
        boolean allowed = projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())
                .map(member -> hasPermission(member, permission))
                .orElse(false);
        if (!allowed) {
            deny(caller, action, projectId);
        }
    }

    /**
     * The project a version belongs to. The platform-admin shortcut runs before
     * this in every caller, so a version attached to no project is reachable by
     * nobody else: there is no membership that could grant access to it.
     */
    private UUID owningProjectId(UUID versionId) {
        return projectRepository.findIdByVersionId(versionId)
                .orElseThrow(() -> {
                    logger.warn("Version {} is not attached to any project; access denied", versionId);
                    return new ForbiddenException("You are not allowed to access this version");
                });
    }

    private Session requireSession() {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return session;
    }

    private boolean isPlatformAdmin(User caller) {
        return caller.getPlatformRole() == PlatformRole.ADMIN;
    }

    private boolean hasPermission(ProjectMember member, ProjectMemberPermission permission) {
        return member.getRoles().stream()
                .anyMatch(role -> role.getPermissions().contains(permission));
    }

    private void deny(User caller, String action, UUID projectId) {
        logger.warn("User {} is not allowed to {} project {}", caller.getMail(), action, projectId);
        throw new ForbiddenException("You are not allowed to " + action + " this project");
    }
}
