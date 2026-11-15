package org.enerscope.version.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.enums.VerticalEnum;
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
import org.enerscope.version.dto.ProjectAccessDTO;
import org.enerscope.version.dto.VersionAccessDTO;
import org.enerscope.version.dto.VersionRoleGrantDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionAccessSource;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VersionAccessServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
    private final VersionMemberRepository versionMemberRepository = mock(VersionMemberRepository.class);
    private final AppLogger logger = mock(AppLogger.class);
    private final VersionAccessService service = new VersionAccessService(projectRepository, projectMemberRepository,
            versionMemberRepository, new ProjectAccessGuard(projectMemberRepository, logger), logger);

    private final UUID projectId = UUID.randomUUID();
    private Project project;
    private Version simulated;
    private Version plain;
    private User caller;

    @BeforeEach
    void setUp() {
        project = new Project("Grid", "Grid", new Organization("Acme"));
        simulated = version("Simulated", Instant.parse("2026-01-01T00:00:00Z"));
        plain = version("Plain", Instant.parse("2026-02-01T00:00:00Z"));
        project.addVersion(plain);
        project.addVersion(simulated);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        caller = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe", PlatformRole.USER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void versionRolesReplaceTheProjectRoleOnlyOnTheVersionsThatHaveThem() {
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);
        VersionMember member = versionMember(simulated, role(VersionMemberType.SIMULATOR, null));
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), any())).thenReturn(List.of(member));

        ProjectAccessDTO access = service.resolveForProject(projectId);

        assertEquals(ProjectMemberType.EDITOR, access.projectRole());
        assertEquals(List.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT), access.projectPermissions());
        assertFalse(access.canManageProject());
        assertFalse(access.canCreateRootVersions());
        assertFalse(access.platformAdmin());

        VersionAccessDTO simulator = access.versions().getFirst();
        assertEquals(simulated.getId(), simulator.versionId());
        assertEquals(VersionAccessSource.VERSION_ROLES, simulator.source());
        assertEquals(List.of(new VersionRoleGrantDTO(VersionMemberType.SIMULATOR, null)), simulator.roles());
        assertEquals("SIMULATOR", simulator.effectiveRole());
        assertTrue(simulator.editableVerticals().isEmpty());
        assertTrue(simulator.canView());
        assertFalse(simulator.canEdit());
        assertFalse(simulator.canEditConnections());
        assertTrue(simulator.canSimulate());
        assertFalse(simulator.canManage());
        assertFalse(simulator.canManageRoles());
        assertTrue(simulator.canViewEconomicResults());
        assertTrue(simulator.canViewEconomicConfiguration());
        assertFalse(simulator.canEditEconomicConfiguration());

        VersionAccessDTO fallback = access.versions().get(1);
        assertEquals(plain.getId(), fallback.versionId());
        assertEquals(VersionAccessSource.PROJECT_FALLBACK, fallback.source());
        assertTrue(fallback.roles().isEmpty());
        assertEquals("EDITOR", fallback.effectiveRole());
        assertEquals(List.of(VerticalEnum.values()), fallback.editableVerticals());
        assertTrue(fallback.canEdit());
        assertTrue(fallback.canSimulate());
        assertFalse(fallback.canManage());
        assertFalse(fallback.canManageRoles());
        assertTrue(fallback.canEditEconomicConfiguration());
    }

    @Test
    void editorRolesListTheirVerticalsAndTheHighestRoleIsReported() {
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);
        VersionMember member = versionMember(simulated,
                role(VersionMemberType.SIMULATOR, null),
                role(VersionMemberType.EDITOR, VerticalEnum.TRANSPORTATION));
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), any())).thenReturn(List.of(member));

        VersionAccessDTO access = service.resolveForProject(projectId).versions().getFirst();

        assertEquals("EDITOR", access.effectiveRole());
        assertEquals(List.of(
                new VersionRoleGrantDTO(VersionMemberType.EDITOR, VerticalEnum.TRANSPORTATION),
                new VersionRoleGrantDTO(VersionMemberType.SIMULATOR, null)), access.roles());
        assertEquals(List.of(VerticalEnum.TRANSPORTATION, VerticalEnum.INTERNAL_CONSUMPTION), access.editableVerticals());
        assertTrue(access.canEdit());
        assertTrue(access.canSimulate());
        assertFalse(access.canManage());
    }

    @Test
    void projectAdminWithoutVersionRolesCanManageEveryVersion() {
        authenticate(caller);
        joinProject(ProjectMemberType.ADMIN);
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), any())).thenReturn(List.of());

        ProjectAccessDTO access = service.resolveForProject(projectId);

        assertTrue(access.canManageProject());
        assertTrue(access.canCreateRootVersions());
        assertTrue(access.versions().stream().allMatch(VersionAccessDTO::canManage));
        assertTrue(access.versions().stream().allMatch(VersionAccessDTO::canManageRoles));
        assertTrue(access.versions().stream().allMatch(v -> "ADMIN".equals(v.effectiveRole())));
        assertTrue(access.versions().stream().allMatch(v -> v.source() == VersionAccessSource.PROJECT_ADMIN));
    }

    @Test
    void projectEditorWithoutVersionRolesCannotCreateRootVersionsButStillEditsThemAll() {
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);

        ProjectAccessDTO access = service.resolveForProject(projectId);

        assertFalse(access.canManageProject());
        assertFalse(access.canCreateRootVersions());
        assertTrue(access.versions().stream().allMatch(VersionAccessDTO::canEdit));
    }

    @Test
    void projectAdminHoldingANarrowVersionRoleKeepsFullAccessAndStillSeesTheRole() {
        authenticate(caller);
        joinProject(ProjectMemberType.ADMIN);
        VersionMember member = versionMember(simulated, role(VersionMemberType.SIMULATOR, null));
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), any())).thenReturn(List.of(member));

        VersionAccessDTO narrowed = service.resolveForProject(projectId).versions().getFirst();

        assertEquals(simulated.getId(), narrowed.versionId());
        assertEquals(VersionAccessSource.PROJECT_ADMIN, narrowed.source());
        assertEquals(List.of(new VersionRoleGrantDTO(VersionMemberType.SIMULATOR, null)), narrowed.roles());
        assertEquals("ADMIN", narrowed.effectiveRole());
        assertEquals(List.of(VerticalEnum.values()), narrowed.editableVerticals());
        assertTrue(narrowed.canView());
        assertTrue(narrowed.canEdit());
        assertTrue(narrowed.canEditConnections());
        assertTrue(narrowed.canSimulate());
        assertTrue(narrowed.canManage());
        assertTrue(narrowed.canManageRoles());
        assertTrue(narrowed.canEditEconomicConfiguration());
    }

    @Test
    void projectEditorHoldingANarrowVersionRoleDoesNotGetTheProjectAdminException() {
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);
        VersionMember member = versionMember(simulated, role(VersionMemberType.SIMULATOR, null));
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), any())).thenReturn(List.of(member));

        VersionAccessDTO narrowed = service.resolveForProject(projectId).versions().getFirst();

        assertEquals(VersionAccessSource.VERSION_ROLES, narrowed.source());
        assertFalse(narrowed.canManage());
        assertFalse(narrowed.canManageRoles());
        assertFalse(narrowed.canEdit());
    }

    @Test
    void aVersionAdminWhoIsNotAProjectAdminManagesTheVersionButNotItsRoles() {
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);
        VersionMember member = versionMember(simulated, role(VersionMemberType.ADMIN, null));
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), any())).thenReturn(List.of(member));

        VersionAccessDTO administered = service.resolveForProject(projectId).versions().getFirst();

        assertEquals(VersionAccessSource.VERSION_ROLES, administered.source());
        assertEquals("ADMIN", administered.effectiveRole());
        assertTrue(administered.canManage());
        assertFalse(administered.canManageRoles());
    }

    @Test
    void anInactiveProjectMembershipIsRejectedWith403() {
        authenticate(caller);
        joinProject(ProjectMemberType.ADMIN);
        projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()).orElseThrow().deactivate();

        assertThrows(ForbiddenException.class, () -> service.resolveForProject(projectId));
        verifyNoInteractions(versionMemberRepository);
    }

    @Test
    void platformAdminGetsFullAccessWithoutLookingUpVersionRoles() {
        caller = User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);
        authenticate(caller);

        ProjectAccessDTO access = service.resolveForProject(projectId);

        assertTrue(access.platformAdmin());
        assertNull(access.projectRole());
        assertTrue(access.canManageProject());
        assertTrue(access.canCreateRootVersions());
        assertTrue(access.versions().stream().allMatch(v -> v.source() == VersionAccessSource.PLATFORM_ADMIN
                && v.canManage() && v.canManageRoles() && v.canEdit() && v.canSimulate() && "ADMIN".equals(v.effectiveRole())));
        verifyNoInteractions(versionMemberRepository);
    }

    @Test
    void inactiveVersionsAreLeftOut() {
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);
        plain.deactivate();
        when(versionMemberRepository.findByUserIdAndVersionIdIn(eq(caller.getId()), eq(List.of(simulated.getId()))))
                .thenReturn(List.of());

        List<VersionAccessDTO> versions = service.resolveForProject(projectId).versions();

        assertEquals(List.of(simulated.getId()), versions.stream().map(VersionAccessDTO::versionId).toList());
    }

    @Test
    void projectWithoutVersionsSkipsTheVersionRoleLookup() {
        project = new Project("Empty", "Empty", new Organization("Acme"));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        authenticate(caller);
        joinProject(ProjectMemberType.EDITOR);

        assertTrue(service.resolveForProject(projectId).versions().isEmpty());
        verifyNoInteractions(versionMemberRepository);
    }

    @Test
    void nonMemberIsRejectedWith403() {
        authenticate(caller);

        assertThrows(ForbiddenException.class, () -> service.resolveForProject(projectId));
        verifyNoInteractions(versionMemberRepository);
    }

    @Test
    void unknownProjectIsRejectedWith403ForANonAdmin() {
        authenticate(caller);

        assertThrows(ForbiddenException.class, () -> service.resolveForProject(UUID.randomUUID()));
        verifyNoInteractions(versionMemberRepository);
    }

    @Test
    void unknownProjectIsRejectedWith404ForAPlatformAdmin() {
        caller = User.fromJwtClaims(UUID.randomUUID(), "admin@enerscope.org", "Admin", "User", PlatformRole.ADMIN);
        authenticate(caller);

        assertThrows(EntityNotFoundException.class, () -> service.resolveForProject(UUID.randomUUID()));
    }

    @Test
    void unauthenticatedCallerIsRejectedWith401() {
        assertThrows(UnauthorizedException.class, () -> service.resolveForProject(projectId));
    }

    private void joinProject(ProjectMemberType type) {
        ProjectMember member = new ProjectMember(new User("jane@enerscope.org", "Jane", "Doe", "hashed"), project);
        member.addRole(new ProjectMemberRole(type.name(), type, ProjectMember.defaultPermissionsFor(type)));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId())).thenReturn(Optional.of(member));
    }

    private Version version(String name, Instant createdAt) {
        Version version = new Version(name, null, null, null, null, null);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(version, "createdAt", createdAt);
        return version;
    }

    private VersionMember versionMember(Version version, VersionMemberRole... roles) {
        VersionMember member = new VersionMember(new User("jane@enerscope.org", "Jane", "Doe", "hashed"), version);
        for (VersionMemberRole role : roles) {
            member.addRole(role);
        }
        return member;
    }

    private VersionMemberRole role(VersionMemberType type, VerticalEnum vertical) {
        return new VersionMemberRole(type.name(), type, EnumSet.copyOf(VersionMember.defaultPermissionsFor(type)), vertical);
    }

    private void authenticate(User user) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        token.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
