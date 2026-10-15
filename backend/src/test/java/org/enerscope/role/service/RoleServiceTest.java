package org.enerscope.role.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.role.dto.RoleAssignmentRequestDTO;
import org.enerscope.role.dto.RoleLevel;
import org.enerscope.role.dto.UserRoleDTO;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private RoleAuthorizationService roleAuthorizationService;
    @Mock
    private AppLogger logger;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(userRepository, organizationRepository, organizationMemberRepository,
                projectRepository, projectMemberRepository, roleAuthorizationService, logger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- listRoles -------------------------------------------------------

    @Test
    void listRolesReturnsPlatformOrganizationAndProjectRoles() {
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        ReflectionTestUtils.setField(target, "id", UUID.randomUUID());
        authenticateAs(target);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByUserId(target.getId()))
                .thenReturn(List.of(organizationMembership(target, "Acme", OrganizationMemberType.OWNER)));
        when(projectMemberRepository.findByUserId(target.getId()))
                .thenReturn(List.of(projectMembership(target, "Grid Expansion", ProjectMemberType.EDITOR)));

        List<UserRoleDTO> roles = roleService.listRoles(target.getId());

        assertEquals(3, roles.size());
        assertTrue(roles.stream().anyMatch(r -> r.level() == RoleLevel.PLATFORM && "USER".equals(r.roleName())));
        assertTrue(roles.stream().anyMatch(r -> r.level() == RoleLevel.ORGANIZATION && "OWNER".equals(r.roleName())));
        assertTrue(roles.stream().anyMatch(r -> r.level() == RoleLevel.PROJECT && "EDITOR".equals(r.roleName())));
    }

    @Test
    void listRolesAllowsPlatformAdminToViewAnyUser() {
        UUID targetId = UUID.randomUUID();
        authenticateAs(admin());
        when(userRepository.findById(targetId)).thenReturn(
                Optional.of(new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER)));
        when(organizationMemberRepository.findByUserId(targetId)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(targetId)).thenReturn(List.of());

        assertEquals(1, roleService.listRoles(targetId).size());
    }

    @Test
    void listRolesRejectsCallerViewingSomeoneElsesRolesWith403() {
        UUID targetId = UUID.randomUUID();
        User caller = new User("outsider@enerscope.org", "Out", "Sider", "hashed", PlatformRole.USER);
        authenticateAs(caller);

        assertThrows(ForbiddenException.class, () -> roleService.listRoles(targetId));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void listRolesRejectsUnauthenticatedCaller() {
        assertThrows(UnauthorizedException.class, () -> roleService.listRoles(UUID.randomUUID()));
    }

    // ---- assignRole --------------------------------------------------------

    @Test
    void assignRoleSetsPlatformRole() {
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        roleService.assignRole(target.getId(), new RoleAssignmentRequestDTO(RoleLevel.PLATFORM, null, "ADMIN"));

        assertEquals(PlatformRole.ADMIN, target.getPlatformRole());
        verify(roleAuthorizationService).assertCanAssign(RoleLevel.PLATFORM, null);
        verify(userRepository).save(target);
    }

    @Test
    void assignRoleCreatesOrganizationMembershipForAnOutsideUser() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, target.getId()))
                .thenReturn(Optional.empty());
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        roleService.assignRole(target.getId(),
                new RoleAssignmentRequestDTO(RoleLevel.ORGANIZATION, orgId, "MEMBER"));

        verify(organizationMemberRepository).save(any(OrganizationMember.class));
        assertTrue(organization.getMembers().stream().anyMatch(m -> m.getUser() == target));
    }

    @Test
    void assignRoleAddsProjectRoleToExistingMembership() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "desc", new Organization("Acme"));
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        ProjectMember existing = new ProjectMember(target, project);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, target.getId()))
                .thenReturn(Optional.of(existing));
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

        roleService.assignRole(target.getId(), new RoleAssignmentRequestDTO(RoleLevel.PROJECT, projectId, "EDITOR"));

        assertEquals(1, existing.getRoles().size());
        ProjectMemberRole role = existing.getRoles().iterator().next();
        assertEquals(ProjectMemberType.EDITOR, role.getMemberType());
    }

    @Test
    void assignRoleRejectsUnknownRoleType() {
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThrows(IllegalArgumentException.class, () -> roleService.assignRole(target.getId(),
                new RoleAssignmentRequestDTO(RoleLevel.PLATFORM, null, "SUPERUSER")));
    }

    @Test
    void assignRolePropagatesForbiddenFromAuthorizationCheck() {
        UUID projectId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        doThrow(new ForbiddenException("You are not allowed to assign roles at this level"))
                .when(roleAuthorizationService).assertCanAssign(RoleLevel.PROJECT, projectId);

        assertThrows(ForbiddenException.class, () -> roleService.assignRole(targetId,
                new RoleAssignmentRequestDTO(RoleLevel.PROJECT, projectId, "EDITOR")));
        verify(userRepository, never()).findById(any());
    }

    // ---- removeRole --------------------------------------------------------

    @Test
    void removeRoleResetsPlatformRoleToUser() {
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.ADMIN);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        roleService.removeRole(target.getId(), new RoleAssignmentRequestDTO(RoleLevel.PLATFORM, null, "ADMIN"));

        assertEquals(PlatformRole.USER, target.getPlatformRole());
    }

    @Test
    void removeRoleDeletesOrganizationMembershipWhenLastRoleRemoved() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        OrganizationMember member = new OrganizationMember(target, organization);
        member.addRole(new OrganizationMemberRole(OrganizationMemberType.MEMBER.name(),
                OrganizationMemberType.MEMBER, EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, target.getId()))
                .thenReturn(Optional.of(member));

        roleService.removeRole(target.getId(),
                new RoleAssignmentRequestDTO(RoleLevel.ORGANIZATION, orgId, "MEMBER"));

        assertTrue(member.getRoles().isEmpty());
        verify(organizationMemberRepository).delete(member);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void removeRoleKeepsMembershipWhenOtherRolesRemain() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "desc", new Organization("Acme"));
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        ProjectMember member = new ProjectMember(target, project);
        member.addRole(new ProjectMemberRole(ProjectMemberType.ADMIN.name(), ProjectMemberType.ADMIN,
                EnumSet.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)));
        member.addRole(new ProjectMemberRole(ProjectMemberType.EDITOR.name(), ProjectMemberType.EDITOR,
                EnumSet.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT)));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, target.getId()))
                .thenReturn(Optional.of(member));

        roleService.removeRole(target.getId(),
                new RoleAssignmentRequestDTO(RoleLevel.PROJECT, projectId, "ADMIN"));

        assertEquals(1, member.getRoles().size());
        verify(projectMemberRepository).save(member);
        verify(projectMemberRepository, never()).delete(any());
    }

    @Test
    void removeRoleRejectsUserWithNoRolesOnResource() {
        UUID orgId = UUID.randomUUID();
        User target = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, target.getId()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> roleService.removeRole(target.getId(),
                new RoleAssignmentRequestDTO(RoleLevel.ORGANIZATION, orgId, "MEMBER")));
    }

    // ---- helpers ---------------------------------------------------------

    private User admin() {
        return new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private OrganizationMember organizationMembership(User user, String orgName, OrganizationMemberType type) {
        Organization organization = new Organization(orgName);
        OrganizationMember member = new OrganizationMember(user, organization);
        var permissions = type == OrganizationMemberType.OWNER
                ? EnumSet.of(OrganizationMemberPermission.MANAGE_ORGANIZATION, OrganizationMemberPermission.VIEW_ORGANIZATION)
                : EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION);
        member.addRole(new OrganizationMemberRole(type.name(), type, permissions));
        return member;
    }

    private ProjectMember projectMembership(User user, String projectName, ProjectMemberType type) {
        Project project = new Project(projectName, "desc", new Organization("Acme"));
        ProjectMember member = new ProjectMember(user, project);
        var permissions = type == ProjectMemberType.ADMIN
                ? EnumSet.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)
                : EnumSet.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT);
        member.addRole(new ProjectMemberRole(type.name(), type, permissions));
        return member;
    }
}
