package org.enerscope.project.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectAccessGuard}, the single home of the project and
 * version authorization rules. Every check is exercised in its three states:
 * authorized, authenticated but not allowed (403), and no session at all (401).
 */
@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private AppLogger logger;

    private ProjectAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProjectAccessGuard(projectRepository, projectMemberRepository, logger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- assertCanViewProject ------------------------------------------------

    @Test
    void assertCanViewProjectAllowsAnyMember() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("member@enerscope.org");
        authenticateAs(caller);
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(true);

        assertDoesNotThrow(() -> guard.assertCanViewProject(projectId));
    }

    @Test
    void assertCanViewProjectAllowsPlatformAdminWithoutMembershipLookup() {
        authenticateAs(platformAdmin());

        assertDoesNotThrow(() -> guard.assertCanViewProject(UUID.randomUUID()));

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanViewProjectRejectsNonMemberWith403() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("outsider@enerscope.org");
        authenticateAs(caller);
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> guard.assertCanViewProject(projectId));
    }

    @Test
    void assertCanViewProjectRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertCanViewProject(UUID.randomUUID()));

        verifyNoInteractions(projectMemberRepository);
    }

    // ---- assertCanEditProject ------------------------------------------------

    @Test
    void assertCanEditProjectAllowsMemberWithEditPermission() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        assertDoesNotThrow(() -> guard.assertCanEditProject(projectId));
    }

    @Test
    void assertCanEditProjectAllowsPlatformAdminWithoutMembershipLookup() {
        authenticateAs(platformAdmin());

        assertDoesNotThrow(() -> guard.assertCanEditProject(UUID.randomUUID()));

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanEditProjectRejectsMemberWithoutEditPermissionWith403() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("viewer@enerscope.org");
        authenticateAs(caller);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWithPermissions(
                        ProjectMemberType.EDITOR, Set.of(ProjectMemberPermission.VIEW_PROJECT))));

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditProject(projectId));
    }

    @Test
    void assertCanEditProjectRejectsNonMemberWith403() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("outsider@enerscope.org");
        authenticateAs(caller);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditProject(projectId));
    }

    @Test
    void assertCanEditProjectRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertCanEditProject(UUID.randomUUID()));

        verifyNoInteractions(projectMemberRepository);
    }

    // ---- assertCanManageProject ----------------------------------------------

    @Test
    void assertCanManageProjectAllowsProjectAdmin() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("admin@acme.org");
        authenticateAs(caller);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.ADMIN)));

        assertDoesNotThrow(() -> guard.assertCanManageProject(projectId));
    }

    @Test
    void assertCanManageProjectRejectsEditorWith403() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageProject(projectId));
    }

    @Test
    void assertCanManageProjectRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertCanManageProject(UUID.randomUUID()));

        verifyNoInteractions(projectMemberRepository);
    }

    // ---- assertCanEditVersion / assertCanViewVersion -------------------------

    @Test
    void assertCanEditVersionResolvesOwningProjectAndAllowsEditor() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        assertDoesNotThrow(() -> guard.assertCanEditVersion(versionId));
    }

    @Test
    void assertCanEditVersionRejectsCallerOutsideOwningProjectWith403() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("outsider@enerscope.org");
        authenticateAs(caller);
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditVersion(versionId));
    }

    @Test
    void assertCanEditVersionRejectsVersionWithoutOwningProjectWith403() {
        UUID versionId = UUID.randomUUID();
        authenticateAs(regularUser("member@enerscope.org"));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditVersion(versionId));

        verify(projectMemberRepository, never()).findByProjectIdAndUserId(any(), any());
    }

    @Test
    void assertCanEditVersionAllowsPlatformAdminOnOrphanVersion() {
        authenticateAs(platformAdmin());

        assertDoesNotThrow(() -> guard.assertCanEditVersion(UUID.randomUUID()));

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanEditVersionRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertCanEditVersion(UUID.randomUUID()));

        verifyNoInteractions(projectRepository);
    }

    @Test
    void assertCanViewVersionAllowsAnyMemberOfOwningProject() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("member@enerscope.org");
        authenticateAs(caller);
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(true);

        assertDoesNotThrow(() -> guard.assertCanViewVersion(versionId));
    }

    @Test
    void assertCanViewVersionRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertCanViewVersion(UUID.randomUUID()));

        verifyNoInteractions(projectRepository);
    }

    // ---- assertIsPlatformAdmin -----------------------------------------------

    @Test
    void assertIsPlatformAdminAllowsPlatformAdmin() {
        authenticateAs(platformAdmin());

        assertDoesNotThrow(() -> guard.assertIsPlatformAdmin("create detached versions"));
    }

    @Test
    void assertIsPlatformAdminRejectsRegularUserWith403() {
        authenticateAs(regularUser("member@enerscope.org"));

        assertThrows(ForbiddenException.class, () -> guard.assertIsPlatformAdmin("create detached versions"));
    }

    @Test
    void assertIsPlatformAdminRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertIsPlatformAdmin("create detached versions"));
    }

    @Test
    void assertCanManageProjectDeniesAnAdminAfterTheyAreDemotedToEditor() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("demoted@enerscope.org");
        authenticateAs(caller);
        ProjectMember member = memberWith(ProjectMemberType.ADMIN);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(member));
        assertDoesNotThrow(() -> guard.assertCanManageProject(projectId));

        member.changeRole(ProjectMemberType.EDITOR, ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR));

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageProject(projectId));
    }

    @Test
    void assertCanEditProjectStillAllowsAnAdminAfterTheyAreDemotedToEditor() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("demoted@enerscope.org");
        authenticateAs(caller);
        ProjectMember member = memberWith(ProjectMemberType.ADMIN);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(member));

        member.changeRole(ProjectMemberType.EDITOR, ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR));

        assertDoesNotThrow(() -> guard.assertCanEditProject(projectId));
    }

    @Test
    void assertCanManageProjectAllowsAnEditorAfterTheyArePromotedToAdmin() {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("promoted@enerscope.org");
        authenticateAs(caller);
        ProjectMember member = memberWith(ProjectMemberType.EDITOR);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(member));
        assertThrows(ForbiddenException.class, () -> guard.assertCanManageProject(projectId));

        member.changeRole(ProjectMemberType.ADMIN, ProjectMember.defaultPermissionsFor(ProjectMemberType.ADMIN));

        assertDoesNotThrow(() -> guard.assertCanManageProject(projectId));
    }

    @Test
    void changingTheRoleDoesNotShareThePermissionSetWithTheDefaults() {
        ProjectMember member = memberWith(ProjectMemberType.EDITOR);
        member.changeRole(ProjectMemberType.ADMIN, ProjectMember.defaultPermissionsFor(ProjectMemberType.ADMIN));

        member.getRoles().iterator().next().getPermissions().remove(ProjectMemberPermission.MANAGE_PROJECT);

        assertEquals(true, ProjectMember.defaultPermissionsFor(ProjectMemberType.ADMIN)
                .contains(ProjectMemberPermission.MANAGE_PROJECT));
    }

    // ---- helpers -------------------------------------------------------------

    private User platformAdmin() {
        return User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);
    }

    private User regularUser(String mail) {
        return User.fromJwtClaims(UUID.randomUUID(), mail, "Jane", "Doe", PlatformRole.USER);
    }

    /** A membership carrying the default permissions of the given member type. */
    private ProjectMember memberWith(ProjectMemberType memberType) {
        return memberWithPermissions(memberType, memberType == ProjectMemberType.ADMIN
                ? Set.of(ProjectMemberPermission.MANAGE_PROJECT,
                        ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)
                : Set.of(ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT));
    }

    /**
     * A membership with an explicit permission set, which may not match the
     * defaults for its member type: the guard reads permissions, never the type
     * label, and that is worth being able to test on its own.
     */
    private ProjectMember memberWithPermissions(ProjectMemberType memberType,
                                                Set<ProjectMemberPermission> permissions) {
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(memberType.name(), memberType, permissions));
        return member;
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
