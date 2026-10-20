package org.enerscope.user.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.service.ProjectService;
import org.enerscope.session.model.Session;
import org.enerscope.user.dto.RoleLevel;
import org.enerscope.user.dto.UserRoleDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private ProjectService projectService;

    private UserRoleService userRoleService;

    @BeforeEach
    void setUp() {
        userRoleService = new UserRoleService(userRepository, organizationMemberRepository, projectMemberRepository,
                organizationService, projectService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRolesIncludesPlatformRole() {
        User target = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe", PlatformRole.USER);
        authenticateAs(target);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByUserIdWithOrganization(target.getId())).thenReturn(List.of());
        when(projectMemberRepository.findByUserIdWithProject(target.getId())).thenReturn(List.of());

        List<UserRoleDTO> roles = userRoleService.listRoles(target.getId());

        assertEquals(1, roles.size());
        assertEquals(RoleLevel.PLATFORM, roles.get(0).level());
        assertEquals("USER", roles.get(0).roleType());
    }

    @Test
    void listRolesIncludesOrganizationMemberships() {
        User target = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe", PlatformRole.USER);
        authenticateAs(target);
        Organization organization = new Organization("Acme");
        OrganizationMember membership = new OrganizationMember(target, organization);
        membership.addRole(new OrganizationMemberRole(
                OrganizationMemberType.OWNER.name(), OrganizationMemberType.OWNER,
                Set.of(OrganizationMemberPermission.MANAGE_ORGANIZATION, OrganizationMemberPermission.VIEW_ORGANIZATION)));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByUserIdWithOrganization(target.getId())).thenReturn(List.of(membership));
        when(projectMemberRepository.findByUserIdWithProject(target.getId())).thenReturn(List.of());

        List<UserRoleDTO> roles = userRoleService.listRoles(target.getId());

        assertTrue(roles.stream().anyMatch(r -> r.level() == RoleLevel.ORGANIZATION
                && "Acme".equals(r.resourceName()) && "OWNER".equals(r.roleType())));
    }

    @Test
    void listRolesIncludesProjectMemberships() {
        User target = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe", PlatformRole.USER);
        authenticateAs(target);
        Project project = new Project("Grid Expansion", "desc", new Organization("Acme"));
        ProjectMember membership = new ProjectMember(target, project);
        membership.addRole(new ProjectMemberRole(
                ProjectMemberType.ADMIN.name(), ProjectMemberType.ADMIN,
                Set.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByUserIdWithOrganization(target.getId())).thenReturn(List.of());
        when(projectMemberRepository.findByUserIdWithProject(target.getId())).thenReturn(List.of(membership));

        List<UserRoleDTO> roles = userRoleService.listRoles(target.getId());

        assertTrue(roles.stream().anyMatch(r -> r.level() == RoleLevel.PROJECT
                && "Grid Expansion".equals(r.resourceName()) && "ADMIN".equals(r.roleType())));
    }

    @Test
    void listRolesAllowsPlatformAdminForAnyUser() {
        User admin = new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        authenticateAs(admin);
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByUserIdWithOrganization(target.getId())).thenReturn(List.of());
        when(projectMemberRepository.findByUserIdWithProject(target.getId())).thenReturn(List.of());

        assertEquals(1, userRoleService.listRoles(target.getId()).size());
    }

    @Test
    void listRolesRejectsOtherUserWithoutAdmin() {
        User caller = User.fromJwtClaims(UUID.randomUUID(), "caller@enerscope.org", "Call", "Er", PlatformRole.USER);
        authenticateAs(caller);
        User target = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThrows(ForbiddenException.class, () -> userRoleService.listRoles(target.getId()));
    }

    @Test
    void listRolesRejectsUnauthenticatedCaller() {
        UUID userId = UUID.randomUUID();

        assertThrows(UnauthorizedException.class, () -> userRoleService.listRoles(userId));
    }

    @Test
    void listRolesRejectsUnknownUser() {
        User admin = new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        authenticateAs(admin);
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userRoleService.listRoles(userId));
    }

    // ---- assignPlatformRole ---------------------------------------------------

    @Test
    void assignPlatformRoleAllowsPlatformAdmin() {
        User admin = new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        authenticateAs(admin);
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userRoleService.assignPlatformRole(target.getId(), PlatformRole.ADMIN);

        assertEquals(PlatformRole.ADMIN, updated.getPlatformRole());
    }

    @Test
    void assignPlatformRoleRejectsNonAdminCaller() {
        User caller = new User("caller@enerscope.org", "Call", "Er", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        UUID targetId = UUID.randomUUID();

        assertThrows(ForbiddenException.class,
                () -> userRoleService.assignPlatformRole(targetId, PlatformRole.ADMIN));
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignPlatformRoleRejectsUnauthenticatedCaller() {
        UUID targetId = UUID.randomUUID();

        assertThrows(UnauthorizedException.class,
                () -> userRoleService.assignPlatformRole(targetId, PlatformRole.ADMIN));
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignPlatformRoleRejectsUnknownUser() {
        User admin = new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        authenticateAs(admin);
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.assignPlatformRole(targetId, PlatformRole.ADMIN));
        verify(userRepository, never()).save(any());
    }

    // ---- assignRole (generic, any level) --------------------------------------

    @Test
    void assignRoleDelegatesToOrganizationServiceForOrganizationLevel() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        userRoleService.assignRole(userId, RoleLevel.ORGANIZATION, orgId, "OWNER");

        verify(organizationService).addMember(eq(orgId),
                eq(new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.OWNER)));
    }

    @Test
    void assignRoleDelegatesToProjectServiceForProjectLevel() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        userRoleService.assignRole(userId, RoleLevel.PROJECT, projectId, "EDITOR");

        verify(projectService).addMember(eq(projectId),
                eq(new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
    }

    @Test
    void assignRoleDelegatesToPlatformRoleAssignmentForPlatformLevel() {
        User admin = new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        authenticateAs(admin);
        UUID userId = UUID.randomUUID();
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userRoleService.assignRole(userId, RoleLevel.PLATFORM, null, "ADMIN");

        assertEquals(PlatformRole.ADMIN, target.getPlatformRole());
    }

    @Test
    void assignRoleRejectsOrganizationLevelWithoutResourceId() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.assignRole(userId, RoleLevel.ORGANIZATION, null, "OWNER"));
        verify(organizationService, never()).addMember(any(), any());
    }

    @Test
    void assignRoleRejectsProjectLevelWithoutResourceId() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.assignRole(userId, RoleLevel.PROJECT, null, "EDITOR"));
        verify(projectService, never()).addMember(any(), any());
    }

    @Test
    void assignRoleRejectsPlatformLevelWithResourceId() {
        UUID userId = UUID.randomUUID();
        UUID someResourceId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.assignRole(userId, RoleLevel.PLATFORM, someResourceId, "ADMIN"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignRolePropagatesForbiddenFromOrganizationService() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not allowed to manage users in this organization"))
                .when(organizationService).addMember(eq(orgId), any());

        assertThrows(ForbiddenException.class,
                () -> userRoleService.assignRole(userId, RoleLevel.ORGANIZATION, orgId, "OWNER"));
    }

    // ---- removeRole (generic, any level) --------------------------------------

    @Test
    void removeRoleDelegatesToOrganizationServiceForOrganizationLevel() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        userRoleService.removeRole(userId, RoleLevel.ORGANIZATION, orgId);

        verify(organizationService).removeMember(orgId, userId);
    }

    @Test
    void removeRoleDelegatesToProjectServiceForProjectLevel() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        userRoleService.removeRole(userId, RoleLevel.PROJECT, projectId);

        verify(projectService).removeMember(projectId, userId);
    }

    @Test
    void removeRoleRejectsPlatformLevel() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.removeRole(userId, RoleLevel.PLATFORM, null));
    }

    @Test
    void removeRoleRejectsOrganizationLevelWithoutResourceId() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.removeRole(userId, RoleLevel.ORGANIZATION, null));
        verify(organizationService, never()).removeMember(any(), any());
    }

    @Test
    void removeRoleRejectsProjectLevelWithoutResourceId() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.removeRole(userId, RoleLevel.PROJECT, null));
        verify(projectService, never()).removeMember(any(), any());
    }

    @Test
    void removeRolePropagatesForbiddenFromProjectService() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not allowed to manage members in this project"))
                .when(projectService).removeMember(projectId, userId);

        assertThrows(ForbiddenException.class,
                () -> userRoleService.removeRole(userId, RoleLevel.PROJECT, projectId));
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
