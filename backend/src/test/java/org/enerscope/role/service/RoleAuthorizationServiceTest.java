package org.enerscope.role.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.role.dto.RoleLevel;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuthorizationServiceTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private RoleAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new RoleAuthorizationService(organizationMemberRepository, projectRepository, projectMemberRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void platformAdminCanAssignAnyLevel() {
        authenticateAs(admin());

        assertDoesNotThrow(() -> service.assertCanAssign(RoleLevel.PLATFORM, null));
        assertDoesNotThrow(() -> service.assertCanAssign(RoleLevel.ORGANIZATION, UUID.randomUUID()));
        assertDoesNotThrow(() -> service.assertCanAssign(RoleLevel.PROJECT, UUID.randomUUID()));
    }

    @Test
    void nonAdminCanNeverAssignPlatformRole() {
        authenticateAs(new User("owner@enerscope.org", "Ow", "Ner", "hashed", PlatformRole.USER));

        assertThrows(ForbiddenException.class, () -> service.assertCanAssign(RoleLevel.PLATFORM, null));
    }

    @Test
    void organizationOwnerCanAssignOrganizationRole() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User owner = new User("owner@enerscope.org", "Ow", "Ner", "hashed", PlatformRole.USER);
        authenticateAs(owner);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, owner.getId()))
                .thenReturn(Optional.of(ownerMembership(owner, organization)));

        assertDoesNotThrow(() -> service.assertCanAssign(RoleLevel.ORGANIZATION, orgId));
    }

    @Test
    void organizationMemberWithoutManagePermissionCannotAssignOrganizationRole() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User plainMember = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(plainMember);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, plainMember.getId()))
                .thenReturn(Optional.of(viewOnlyMembership(plainMember, organization)));

        assertThrows(ForbiddenException.class, () -> service.assertCanAssign(RoleLevel.ORGANIZATION, orgId));
    }

    @Test
    void organizationOwnerCanAssignProjectRoleInOwnOrganizationWithoutExplicitProjectMembership() {
        UUID projectId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        Project project = new Project("Grid Expansion", "desc", organization);
        User owner = new User("owner@enerscope.org", "Ow", "Ner", "hashed", PlatformRole.USER);
        authenticateAs(owner);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, owner.getId())).thenReturn(Optional.empty());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), owner.getId()))
                .thenReturn(Optional.of(ownerMembership(owner, organization)));

        assertDoesNotThrow(() -> service.assertCanAssign(RoleLevel.PROJECT, projectId));
    }

    @Test
    void projectAdminCanAssignProjectRoleInOwnProject() {
        UUID projectId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        User projectAdmin = new User("padmin@enerscope.org", "P", "Admin", "hashed", PlatformRole.USER);
        authenticateAs(projectAdmin);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, projectAdmin.getId()))
                .thenReturn(Optional.of(projectMember(projectAdmin, project, ProjectMemberType.ADMIN)));

        assertDoesNotThrow(() -> service.assertCanAssign(RoleLevel.PROJECT, projectId));
    }

    @Test
    void projectAdminCannotAssignOrganizationRole() {
        UUID orgId = UUID.randomUUID();
        User projectAdmin = new User("padmin@enerscope.org", "P", "Admin", "hashed", PlatformRole.USER);
        authenticateAs(projectAdmin);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, projectAdmin.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.assertCanAssign(RoleLevel.ORGANIZATION, orgId));
    }

    @Test
    void projectAdminCannotAssignRoleInADifferentProject() {
        UUID otherProjectId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        Project otherProject = new Project("Other project", "desc", organization);
        User projectAdmin = new User("padmin@enerscope.org", "P", "Admin", "hashed", PlatformRole.USER);
        authenticateAs(projectAdmin);
        when(projectMemberRepository.findByProjectIdAndUserId(otherProjectId, projectAdmin.getId()))
                .thenReturn(Optional.empty());
        when(projectRepository.findById(otherProjectId)).thenReturn(Optional.of(otherProject));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), projectAdmin.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.assertCanAssign(RoleLevel.PROJECT, otherProjectId));
    }

    @Test
    void editorCannotAssignAnyRole() {
        UUID projectId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        Project project = new Project("Grid Expansion", "desc", organization);
        User editor = new User("editor@enerscope.org", "Ed", "Itor", "hashed", PlatformRole.USER);
        authenticateAs(editor);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, editor.getId()))
                .thenReturn(Optional.of(projectMember(editor, project, ProjectMemberType.EDITOR)));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), editor.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.assertCanAssign(RoleLevel.PROJECT, projectId));
    }

    @Test
    void assertCanAssignRejectsUnauthenticatedCaller() {
        assertThrows(UnauthorizedException.class, () -> service.assertCanAssign(RoleLevel.ORGANIZATION, UUID.randomUUID()));
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

    private OrganizationMember ownerMembership(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.OWNER.name(), OrganizationMemberType.OWNER,
                EnumSet.of(OrganizationMemberPermission.MANAGE_ORGANIZATION,
                        OrganizationMemberPermission.VIEW_ORGANIZATION)));
        return member;
    }

    private OrganizationMember viewOnlyMembership(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(), OrganizationMemberType.MEMBER,
                EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        return member;
    }

    private ProjectMember projectMember(User user, Project project, ProjectMemberType type) {
        ProjectMember member = new ProjectMember(user, project);
        var permissions = type == ProjectMemberType.ADMIN
                ? EnumSet.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                        ProjectMemberPermission.VIEW_PROJECT)
                : EnumSet.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT);
        member.addRole(new ProjectMemberRole(type.name(), type, permissions));
        return member;
    }
}
