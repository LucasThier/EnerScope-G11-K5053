package org.enerscope.version.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
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
 * Unit tests for {@link VersionAccessGuard}, the home of every version
 * authorization rule. It wraps a real {@link ProjectAccessGuard} over the
 * same mocked repositories (not a mock): these cases assert real outcomes
 * of the project-permission fallback, and {@code ProjectAccessGuardTest}
 * already covers the project rules on their own.
 */
@ExtendWith(MockitoExtension.class)
class VersionAccessGuardTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private VersionMemberRepository versionMemberRepository;
    @Mock
    private AppLogger logger;

    private VersionAccessGuard guard;

    @BeforeEach
    void setUp() {
        ProjectAccessGuard projectAccessGuard = new ProjectAccessGuard(projectMemberRepository, logger);
        guard = new VersionAccessGuard(projectRepository, versionMemberRepository, projectAccessGuard, logger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- assertCanEditVersion / assertCanViewVersion (project fallback) -----

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
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

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

    // ---- assertCanManageDescendant ---------------------------------------------

    private static final String GENERIC_DELETE_MESSAGE =
            "You are not allowed to delete one or more versions below this one";

    @Test
    void assertCanManageDescendantAllowsAProjectAdminOfTheSameProject() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("admin@enerscope.org");
        authenticateAs(caller);
        when(projectRepository.findIdByVersionId(rootId)).thenReturn(Optional.of(projectId));
        when(projectRepository.findIdByVersionId(childId)).thenReturn(Optional.of(projectId));
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(childId, caller.getId())).thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.ADMIN)));

        assertDoesNotThrow(() -> guard.assertCanManageDescendant(rootId, childId));
    }

    @Test
    void assertCanManageDescendantRefusesWithAGenericMessageWhenTheCallerCannotManageIt() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(projectRepository.findIdByVersionId(rootId)).thenReturn(Optional.of(projectId));
        when(projectRepository.findIdByVersionId(childId)).thenReturn(Optional.of(projectId));
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(childId, caller.getId())).thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> guard.assertCanManageDescendant(rootId, childId));

        assertEquals(GENERIC_DELETE_MESSAGE, ex.getMessage());
    }

    @Test
    void assertCanManageDescendantRefusesADescendantOfAnotherProjectEvenForItsAdmin() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        authenticateAs(regularUser("admin@enerscope.org"));
        when(projectRepository.findIdByVersionId(rootId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(projectRepository.findIdByVersionId(childId)).thenReturn(Optional.of(UUID.randomUUID()));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> guard.assertCanManageDescendant(rootId, childId));

        assertEquals(GENERIC_DELETE_MESSAGE, ex.getMessage());
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanManageDescendantRefusesADescendantWithoutProject() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        authenticateAs(regularUser("admin@enerscope.org"));
        when(projectRepository.findIdByVersionId(rootId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(projectRepository.findIdByVersionId(childId)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageDescendant(rootId, childId));
    }

    @Test
    void assertCanManageDescendantAllowsAPlatformAdminAcrossProjects() {
        authenticateAs(platformAdmin());

        assertDoesNotThrow(() -> guard.assertCanManageDescendant(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(projectRepository);
    }

    // ---- assertCanManageVersion ------------------------------------------------

    @Test
    void assertCanManageVersionAllowsProjectAdmin() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("admin@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.empty());
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.ADMIN)));

        assertDoesNotThrow(() -> guard.assertCanManageVersion(versionId));
    }

    @Test
    void assertCanManageVersionAllowsVersionAdminWithoutConsultingTheProjectRole() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("versionadmin@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.ADMIN, null)));

        assertDoesNotThrow(() -> guard.assertCanManageVersion(versionId));

        verify(projectMemberRepository, never()).findByProjectIdAndUserId(any(), any());
    }

    @Test
    void assertCanManageVersionRejectsVersionEditorWith403WithoutFallingBackToTheProject() {
        // Holding ANY version role means the project permission is never
        // consulted, even though a plain project member always has at least
        // EDIT_PROJECT: falling back here would make the version-level
        // restriction meaningless.
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("versioneditor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageVersion(versionId));

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanManageVersionIgnoresARoleRowOfSomeoneNoLongerInTheProjectAndRejectsWith403() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("removed@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.empty());
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageVersion(versionId));

        verify(versionMemberRepository, never()).findByVersionIdAndUserId(any(), any());
    }

    @Test
    void assertCanManageVersionRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> guard.assertCanManageVersion(UUID.randomUUID()));
    }

    // ---- assertCanEditVersion with a version role -----------------------------

    @Test
    void assertCanEditVersionAllowsVersionEditorWithoutConsultingTheProjectRole() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("versioneditor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertDoesNotThrow(() -> guard.assertCanEditVersion(versionId));

        verify(projectRepository, never()).findIdByVersionId(any());
    }

    // ---- assertCanViewVersion with a version role -----------------------------

    @Test
    void assertCanViewVersionAllowsAnyVersionRoleWithoutConsultingTheProjectRole() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("simulator@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));

        assertDoesNotThrow(() -> guard.assertCanViewVersion(versionId));

        verify(projectRepository, never()).findIdByVersionId(any());
    }

    // ---- assertCanEditNodeVertical ----------------------------------------------

    @Test
    void assertCanEditNodeVerticalAllowsEditorMatchingVertical() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertDoesNotThrow(() -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION));
    }

    @Test
    void assertCanEditNodeVerticalRejectsEditorOfADifferentVerticalWith403WithoutFallingBackToTheProject() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertThrows(ForbiddenException.class,
                () -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.TRANSPORTATION));

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanEditNodeVerticalAllowsAnyEditorForInternalConsumption() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertDoesNotThrow(() -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.INTERNAL_CONSUMPTION));
    }

    @Test
    void assertCanEditNodeVerticalAllowsVersionAdminForAnyVertical() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("admin@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.ADMIN, null)));

        assertDoesNotThrow(() -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.LIQUEFACTION));
    }

    @Test
    void assertCanEditNodeVerticalFallsBackToProjectEditPermission() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("projecteditor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.empty());
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        assertDoesNotThrow(() -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.EXPORT));
    }

    @Test
    void assertCanEditNodeVerticalRejectsVersionSimulatorWith403WithoutFallingBackToTheProject() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("simulator@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));

        assertThrows(ForbiddenException.class,
                () -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION));

        verifyNoInteractions(projectMemberRepository);
    }

    // ---- assertCanEditConnection ------------------------------------------------

    @Test
    void assertCanEditConnectionAllowsAnyVersionEditorRegardlessOfVertical() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.LIQUEFACTION)));

        assertDoesNotThrow(() -> guard.assertCanEditConnection(versionId));
    }

    @Test
    void assertCanEditConnectionRejectsVersionSimulatorWith403WithoutFallingBackToTheProject() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("simulator@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditConnection(versionId));

        verifyNoInteractions(projectMemberRepository);
    }

    // ---- assertCanSimulateVersion ------------------------------------------------

    @Test
    void assertCanSimulateVersionAllowsVersionSimulator() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("simulator@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));

        assertDoesNotThrow(() -> guard.assertCanSimulateVersion(versionId));
    }

    @Test
    void assertCanSimulateVersionAllowsVersionEditorWithoutFallingBackToTheProject() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertDoesNotThrow(() -> guard.assertCanSimulateVersion(versionId));

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanSimulateVersionRejectsVersionSimulatorFromEditingAnythingWithoutFallingBackToTheProject() {
        // A SIMULATOR role carries no EDIT_VERSION permission: the role exists
        // only to (eventually) run the simulation and to view the version. A
        // plain project member always has at least EDIT_PROJECT, so if this
        // fell back to the project permission the SIMULATOR restriction would
        // never actually hold - it must be refused directly instead.
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("simulator@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditVersion(versionId));
        assertThrows(ForbiddenException.class, () -> guard.assertCanEditConnection(versionId));

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void assertCanSimulateVersionAllowsVersionAdmin() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("versionadmin@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.ADMIN, null)));

        assertDoesNotThrow(() -> guard.assertCanSimulateVersion(versionId));
    }

    @Test
    void assertCanSimulateVersionFallsBackToProjectEditPermission() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("projecteditor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.empty());
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        assertDoesNotThrow(() -> guard.assertCanSimulateVersion(versionId));
    }

    @Test
    void assertCanSimulateVersionRejectsCallerWithNeitherVersionRoleNorProjectMembership() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("outsider@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.empty());
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())).thenReturn(Optional.empty());

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> guard.assertCanSimulateVersion(versionId));
        assertEquals("You are not allowed to edit this project", error.getMessage());
    }

    @Test
    void assertCanSimulateVersionRejectsCallerWithoutRolesUsingTheSimulateMessage() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("empty@enerscope.org");
        authenticateAs(caller);
        VersionMember member = new VersionMember(new User("jane@enerscope.org", "Jane", "Doe", "hashed"),
                new Version("v1", null, null, null, null, null));
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.of(member));

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> guard.assertCanSimulateVersion(versionId));
        assertEquals("You are not allowed to simulate this version", error.getMessage());
    }

    @Test
    void assertCanManageVersionRejectsVersionEditorAndSimulatorWithoutFallingBackToTheProject() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        VersionMember member = versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION);
        member.addRole(new VersionMemberRole("SIMULATOR", VersionMemberType.SIMULATOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR), null));
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.of(member));

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageVersion(versionId));
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void guardDecisionsMatchVersionAccessForEveryVersionRole() {
        User caller = regularUser("matrix@enerscope.org");
        authenticateAs(caller);
        List<VersionMember> members = new java.util.ArrayList<>();
        members.add(versionMemberWith(VersionMemberType.ADMIN, null));
        members.add(versionMemberWith(VersionMemberType.SIMULATOR, null));
        for (VerticalEnum vertical : VerticalEnum.values()) {
            members.add(versionMemberWith(VersionMemberType.EDITOR, vertical));
        }
        for (VersionMember member : members) {
            UUID versionId = UUID.randomUUID();
            when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.of(member));
            VersionAccess access = VersionAccess.fromVersionRoles(member.getRoles());

            assertEquals(access.can(VersionCapability.VIEW), passes(() -> guard.assertCanViewVersion(versionId)));
            assertEquals(access.can(VersionCapability.EDIT), passes(() -> guard.assertCanEditVersion(versionId)));
            assertEquals(access.can(VersionCapability.EDIT_CONNECTIONS), passes(() -> guard.assertCanEditConnection(versionId)));
            assertEquals(access.can(VersionCapability.SIMULATE), passes(() -> guard.assertCanSimulateVersion(versionId)));
            assertEquals(access.can(VersionCapability.MANAGE), passes(() -> guard.assertCanManageVersion(versionId)));
            for (VerticalEnum vertical : VerticalEnum.values()) {
                assertEquals(access.canEditVertical(vertical),
                        passes(() -> guard.assertCanEditNodeVertical(versionId, vertical)));
            }
        }
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void guardFallbackMatchesVersionAccessForEveryProjectRole() {
        User caller = regularUser("fallback@enerscope.org");
        authenticateAs(caller);
        for (ProjectMemberType type : ProjectMemberType.values()) {
            UUID versionId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            ProjectMember member = memberWith(type);
            when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId())).thenReturn(Optional.empty());
            when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
            when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())).thenReturn(Optional.of(member));
            VersionAccess access = VersionAccess.fromProjectMembership(Optional.of(member));

            assertEquals(access.can(VersionCapability.VIEW), passes(() -> guard.assertCanViewVersion(versionId)));
            assertEquals(access.can(VersionCapability.EDIT), passes(() -> guard.assertCanEditVersion(versionId)));
            assertEquals(access.can(VersionCapability.EDIT_CONNECTIONS), passes(() -> guard.assertCanEditConnection(versionId)));
            assertEquals(access.can(VersionCapability.SIMULATE), passes(() -> guard.assertCanSimulateVersion(versionId)));
            assertEquals(access.can(VersionCapability.MANAGE), passes(() -> guard.assertCanManageVersion(versionId)));
            assertEquals(access.canEditVertical(VerticalEnum.EXPORT),
                    passes(() -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.EXPORT)));
        }
    }

    @Test
    void assertCanEditNodesJudgesEachNodeByTheVerticalOfItsTypeNotTheStoredOne() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("extraction@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertDoesNotThrow(() -> guard.assertCanEditNodes(versionId,
                List.of(node(NodeTypeEnum.WELL, VerticalEnum.EXPORT))));
        assertThrows(ForbiddenException.class, () -> guard.assertCanEditNodes(versionId,
                List.of(node(NodeTypeEnum.LNG_CAMER, VerticalEnum.EXTRACTION))));
    }

    @Test
    void assertCanEditNodesRejectsWhenOneOfTheVerticalsIsOutsideTheRole() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("extraction@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditNodes(versionId,
                List.of(node(NodeTypeEnum.WELL, VerticalEnum.EXTRACTION),
                        node(NodeTypeEnum.PIPELINE, VerticalEnum.TRANSPORTATION))));
    }

    @Test
    void assertCanEditNodesWithAnUntypedNodeRequiresEditingTheVersion() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("simulator@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanEditNodes(versionId, List.of(new Well())));
    }

    @Test
    void assertCanEditNodesWithNoNodesChecksNothing() {
        authenticateAs(regularUser("anyone@enerscope.org"));

        assertDoesNotThrow(() -> guard.assertCanEditNodes(UUID.randomUUID(), List.of()));

        verifyNoInteractions(versionMemberRepository, projectRepository, projectMemberRepository);
    }

    @Test
    void aProjectAdminHoldingASimulatorRoleStillManagesEditsAndSimulatesEverything() {
        UUID versionId = UUID.randomUUID();
        projectAdminHolding(versionId, versionMemberWith(VersionMemberType.SIMULATOR, null));

        assertDoesNotThrow(() -> guard.assertCanManageVersion(versionId));
        assertDoesNotThrow(() -> guard.assertCanEditVersion(versionId));
        assertDoesNotThrow(() -> guard.assertCanEditConnection(versionId));
        assertDoesNotThrow(() -> guard.assertCanSimulateVersion(versionId));
        assertDoesNotThrow(() -> guard.assertCanViewVersion(versionId));
        for (VerticalEnum vertical : VerticalEnum.values()) {
            assertDoesNotThrow(() -> guard.assertCanEditNodeVertical(versionId, vertical));
        }
    }

    @Test
    void aProjectAdminHoldingAnEditorRoleOfOneVerticalEditsEveryVertical() {
        UUID versionId = UUID.randomUUID();
        projectAdminHolding(versionId, versionMemberWith(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION));

        for (VerticalEnum vertical : VerticalEnum.values()) {
            assertDoesNotThrow(() -> guard.assertCanEditNodeVertical(versionId, vertical));
        }
        assertDoesNotThrow(() -> guard.assertCanManageVersion(versionId));
    }

    @Test
    void aProjectEditorHoldingASimulatorRoleIsStillRefusedTheProjectAdminExceptionDoesNotApplyToIt() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("editor@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.EDITOR)));

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageVersion(versionId));
        assertThrows(ForbiddenException.class, () -> guard.assertCanEditVersion(versionId));
        assertThrows(ForbiddenException.class, () -> guard.assertCanEditConnection(versionId));
        assertThrows(ForbiddenException.class,
                () -> guard.assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION));
    }

    @Test
    void aFormerProjectAdminWithAnInactiveMembershipIsRefusedEvenHoldingAVersionRole() {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("former@enerscope.org");
        authenticateAs(caller);
        ProjectMember former = memberWith(ProjectMemberType.ADMIN);
        former.deactivate();
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.SIMULATOR, null)));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())).thenReturn(Optional.of(former));

        assertThrows(ForbiddenException.class, () -> guard.assertCanManageVersion(versionId));
    }

    @Test
    void aVersionAdminIsAllowedWithoutConsultingTheProjectAtAll() {
        UUID versionId = UUID.randomUUID();
        User caller = regularUser("versionadmin@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(versionMemberWith(VersionMemberType.ADMIN, null)));

        assertDoesNotThrow(() -> guard.assertCanManageVersion(versionId));
        assertDoesNotThrow(() -> guard.assertCanEditVersion(versionId));
        assertDoesNotThrow(() -> guard.assertCanSimulateVersion(versionId));

        verifyNoInteractions(projectRepository, projectMemberRepository);
    }

    private void projectAdminHolding(UUID versionId, VersionMember narrowRole) {
        UUID projectId = UUID.randomUUID();
        User caller = regularUser("projectadmin@enerscope.org");
        authenticateAs(caller);
        when(versionMemberRepository.findEffectiveByVersionIdAndUserId(versionId, caller.getId()))
                .thenReturn(Optional.of(narrowRole));
        when(projectRepository.findIdByVersionId(versionId)).thenReturn(Optional.of(projectId));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.of(memberWith(ProjectMemberType.ADMIN)));
    }

    private static Well node(NodeTypeEnum type, VerticalEnum storedVertical) {
        Well well = new Well();
        well.setType(new NodeTypeData(storedVertical, StructuralRoleEnum.GENERATOR, type));
        return well;
    }

    private boolean passes(Runnable check) {
        try {
            check.run();
            return true;
        } catch (ForbiddenException e) {
            return false;
        }
    }

    // ---- helpers -------------------------------------------------------------

    private User platformAdmin() {
        return User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);
    }

    private User regularUser(String mail) {
        return User.fromJwtClaims(UUID.randomUUID(), mail, "Jane", "Doe", PlatformRole.USER);
    }

    /** A project membership carrying the default permissions of the given member type. */
    private ProjectMember memberWith(ProjectMemberType memberType) {
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        ProjectMember member = new ProjectMember(user, project);
        Set<ProjectMemberPermission> permissions = memberType == ProjectMemberType.ADMIN
                ? Set.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)
                : Set.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT);
        member.addRole(new ProjectMemberRole(memberType.name(), memberType, permissions));
        return member;
    }

    /** A version membership carrying a single role of the given type (and vertical, for EDITOR). */
    private VersionMember versionMemberWith(VersionMemberType memberType, VerticalEnum vertical) {
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        Version version = new Version("v1", null, null, null, null, null);
        VersionMember member = new VersionMember(user, version);
        member.addRole(new VersionMemberRole(memberType.name(), memberType,
                VersionMember.defaultPermissionsFor(memberType), vertical));
        return member;
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
