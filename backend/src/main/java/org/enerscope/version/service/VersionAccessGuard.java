package org.enerscope.version.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.user.model.User;
import org.enerscope.util.AuthUtil;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.repository.VersionMemberRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Authorization rules for versions. Lives in the {@code version} package,
 * not next to {@link ProjectAccessGuard}, because every caller of these
 * methods ({@code VersionService}, {@code VersionMemberService},
 * {@code VersionConflictService}) is itself in {@code version} — the
 * endpoints go straight through version, never through a project one.
 *
 * <p><b>The project-wide fallback only runs for a caller with no version
 * role at all.</b> The moment a {@code VersionMember} row exists for the
 * caller on this version, that row is the sole source of truth for what
 * they may do on it — a version role is a deliberately *narrower* grant
 * (e.g. "{@code EDITOR} of {@code EXTRACTION} only", or "{@code SIMULATOR}:
 * view and simulate, nothing else"), and falling back to the project
 * permission whenever the version role came up short would make that
 * narrowing pointless: every project member already holds at least
 * {@code EDIT_PROJECT}, so the fallback would silently hand back everything
 * the version role was supposed to withhold. A project {@code ADMIN} is
 * still automatically a version {@code ADMIN} too — but only as long as
 * they hold no version role of their own; the fallback is what grants that,
 * and it only ever runs for someone with no row in {@code version_member}.
 * </p>
 *
 * <p>Every method follows the same shape as {@link ProjectAccessGuard}'s: no
 * session is a {@link UnauthorizedException} (401), a platform {@code ADMIN}
 * passes without any lookup, and everyone else needs the matching
 * permission, or gets a {@link ForbiddenException} (403).</p>
 */
@Component
public class VersionAccessGuard {

    private final ProjectRepository projectRepository;
    private final VersionMemberRepository versionMemberRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final AppLogger logger;

    public VersionAccessGuard(ProjectRepository projectRepository,
                              VersionMemberRepository versionMemberRepository,
                              ProjectAccessGuard projectAccessGuard,
                              AppLogger logger) {
        this.projectRepository = projectRepository;
        this.versionMemberRepository = versionMemberRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.logger = logger;
    }

    /**
     * Ensures the caller may read the version. Every version role implies
     * {@code VIEW_VERSION} (see {@code VersionMember}), so merely holding
     * one is always enough; a caller with none falls back to
     * {@code ProjectAccessGuard.assertCanViewProject} on the owning project.
     */
    public void assertCanViewVersion(UUID versionId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        if (findVersionMember(versionId, caller.getId()).isPresent()) {
            return;
        }
        projectAccessGuard.assertCanViewProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller may make a version-wide edit (renaming it,
     * re-parenting it, moving a node — anything not scoped to one
     * vertical; see {@link #assertCanEditNodeVertical} for that). A caller
     * holding a version role needs {@code EDIT_VERSION} *from that role* —
     * a {@code SIMULATOR} role is not enough, and nothing falls back to the
     * project permission for them. Only a caller with no version role at
     * all falls back to {@code assertCanEditProject} on the owning project.
     */
    public void assertCanEditVersion(UUID versionId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        Optional<VersionMember> member = findVersionMember(versionId, caller.getId());
        if (member.isPresent()) {
            requirePermission(member.get(), VersionMemberPermission.EDIT_VERSION, caller, versionId, "edit");
            return;
        }
        projectAccessGuard.assertCanEditProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller may administer the version itself — deleting it,
     * merging a subversion into it, or resolving its conflicts. A caller
     * holding a version role needs {@code MANAGE_VERSION} from it (an
     * {@code ADMIN} role on this version specifically); only a caller with
     * no version role falls back to {@code MANAGE_PROJECT} on the owning
     * project, which is how a project {@code ADMIN} ends up a version
     * {@code ADMIN} too by default.
     */
    public void assertCanManageVersion(UUID versionId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        Optional<VersionMember> member = findVersionMember(versionId, caller.getId());
        if (member.isPresent()) {
            requirePermission(member.get(), VersionMemberPermission.MANAGE_VERSION, caller, versionId, "manage");
            return;
        }
        projectAccessGuard.assertCanManageProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller may edit a node of the given vertical. A caller
     * holding a version role is judged solely by it: an {@code ADMIN} role
     * covers every vertical; an {@code EDITOR} role covers only the one
     * vertical it is scoped to, plus {@link VerticalEnum#INTERNAL_CONSUMPTION}
     * nodes regardless of that scope (not tied to one specialty, like
     * connections — see {@link #assertCanEditConnection}); anything else
     * (e.g. a bare {@code SIMULATOR} role, or an {@code EDITOR} for a
     * different vertical) is refused outright — it does **not** fall back
     * to the project permission. Only a caller with no version role at all
     * falls back to {@code assertCanEditProject} on the owning project.
     */
    public void assertCanEditNodeVertical(UUID versionId, VerticalEnum vertical) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        Optional<VersionMember> member = findVersionMember(versionId, caller.getId());
        if (member.isPresent()) {
            boolean allowed = member.get().getRoles().stream().anyMatch(role -> canEditVertical(role, vertical));
            if (!allowed) {
                deny(caller, versionId, "edit this vertical's nodes in");
            }
            return;
        }
        projectAccessGuard.assertCanEditProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller may edit a connection. Connections are not scoped
     * to one vertical — they link nodes across verticals — so a version
     * role needs {@code EDIT_VERSION} regardless of which vertical its
     * {@code EDITOR} role, if any, is scoped to; a bare {@code SIMULATOR}
     * role is refused outright, with no fallback. Only a caller with no
     * version role at all falls back to {@code assertCanEditProject} on the
     * owning project.
     */
    public void assertCanEditConnection(UUID versionId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        Optional<VersionMember> member = findVersionMember(versionId, caller.getId());
        if (member.isPresent()) {
            requirePermission(member.get(), VersionMemberPermission.EDIT_VERSION, caller, versionId, "edit");
            return;
        }
        projectAccessGuard.assertCanEditProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller may run the simulation for this version. A caller
     * holding a version role needs {@code SIMULATE_VERSION} from it
     * ({@code ADMIN} or {@code SIMULATOR}); only a caller with no version
     * role falls back to {@code assertCanEditProject} on the owning
     * project.
     */
    public void assertCanSimulateVersion(UUID versionId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        Optional<VersionMember> member = findVersionMember(versionId, caller.getId());
        if (member.isPresent()) {
            requirePermission(member.get(), VersionMemberPermission.SIMULATE_VERSION, caller, versionId, "simulate");
            return;
        }
        projectAccessGuard.assertCanEditProject(owningProjectId(versionId));
    }

    /**
     * Ensures the caller is a platform ADMIN, for actions that no project or
     * organization owns — currently creating a version detached from every
     * project. Kept on this guard so {@code VersionService} has one place to
     * ask, even though the rule itself lives in {@link AuthUtil}.
     */
    public void assertIsPlatformAdmin(String action) {
        AuthUtil.requirePlatformAdmin(logger, action);
    }

    /**
     * The project a version belongs to. The platform-admin shortcut runs
     * before this in every caller, so a version attached to no project is
     * reachable by nobody else: there is no membership that could grant
     * access to it.
     */
    private UUID owningProjectId(UUID versionId) {
        return projectRepository.findIdByVersionId(versionId)
                .orElseThrow(() -> {
                    logger.warn("Version {} is not attached to any project; access denied", versionId);
                    return new ForbiddenException("You are not allowed to access this version");
                });
    }

    private Optional<VersionMember> findVersionMember(UUID versionId, UUID userId) {
        return versionMemberRepository.findByVersionIdAndUserId(versionId, userId);
    }

    /** Throws {@link ForbiddenException} unless one of the member's roles carries {@code permission}. */
    private void requirePermission(VersionMember member, VersionMemberPermission permission, User caller,
                                    UUID versionId, String action) {
        boolean allowed = member.getRoles().stream().anyMatch(role -> role.getPermissions().contains(permission));
        if (!allowed) {
            deny(caller, versionId, action);
        }
    }

    /**
     * Whether {@code role} covers edits to a node of {@code vertical}: an
     * ADMIN role (no vertical of its own, applies everywhere) with
     * {@code EDIT_VERSION}; an EDITOR role scoped to this exact vertical; or
     * any EDITOR role when the vertical is
     * {@link VerticalEnum#INTERNAL_CONSUMPTION}, which — like connections —
     * is not tied to one specialty.
     */
    private boolean canEditVertical(VersionMemberRole role, VerticalEnum vertical) {
        if (!role.getPermissions().contains(VersionMemberPermission.EDIT_VERSION)) {
            return false;
        }
        return role.getVertical() == null
                || role.getVertical() == vertical
                || vertical == VerticalEnum.INTERNAL_CONSUMPTION;
    }

    private void deny(User caller, UUID versionId, String action) {
        logger.warn("User {} is not allowed to {} version {}", caller.getMail(), action, versionId);
        throw new ForbiddenException("You are not allowed to " + action + " this version");
    }
}
