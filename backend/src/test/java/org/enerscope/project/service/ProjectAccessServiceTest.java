package org.enerscope.project.service;

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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    private ProjectAccessService service;

    @BeforeEach
    void setUp() {
        service = new ProjectAccessService(projectRepository, projectMemberRepository, organizationMemberRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assertCanViewAllowsPlatformAdminWithoutLookingUpTheProject() {
        UUID projectId = UUID.randomUUID();
        authenticateAs(admin());

        assertDoesNotThrow(() -> service.assertCanView(projectId));
    }

    @Test
    void assertCanViewAllowsAnyProjectMember() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User caller = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), caller.getId()))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(true);

        assertDoesNotThrow(() -> service.assertCanView(projectId));
    }

    @Test
    void assertCanViewAllowsOrganizationOwnerWithoutExplicitProjectMembership() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User owner = new User("owner@enerscope.org", "Ow", "Ner", "hashed", PlatformRole.USER);
        authenticateAs(owner);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), owner.getId()))
                .thenReturn(Optional.of(ownerMembership(owner, organization)));

        assertDoesNotThrow(() -> service.assertCanView(projectId));
    }

    @Test
    void assertCanViewRejectsCallerWithNoRelationToTheProject() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User outsider = new User("outsider@enerscope.org", "Out", "Sider", "hashed", PlatformRole.USER);
        authenticateAs(outsider);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), outsider.getId()))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, outsider.getId())).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.assertCanView(projectId));
    }

    @Test
    void assertCanViewRejectsUnauthenticatedCaller() {
        assertThrows(UnauthorizedException.class, () -> service.assertCanView(UUID.randomUUID()));
    }

    @Test
    void assertCanEditRejectsProjectMemberWithoutEditPermission() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User caller = new User("viewer@enerscope.org", "View", "Er", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), caller.getId()))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.assertCanEdit(projectId));
    }

    @Test
    void assertCanEditAllowsEditorMember() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User editor = new User("editor@enerscope.org", "Ed", "Itor", "hashed", PlatformRole.USER);
        authenticateAs(editor);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), editor.getId()))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, editor.getId()))
                .thenReturn(Optional.of(projectMember(editor, project, ProjectMemberType.EDITOR)));

        assertDoesNotThrow(() -> service.assertCanEdit(projectId));
    }

    @Test
    void assertCanManageRejectsEditorWithoutManagePermission() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User editor = new User("editor@enerscope.org", "Ed", "Itor", "hashed", PlatformRole.USER);
        authenticateAs(editor);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), editor.getId()))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, editor.getId()))
                .thenReturn(Optional.of(projectMember(editor, project, ProjectMemberType.EDITOR)));

        assertThrows(ForbiddenException.class, () -> service.assertCanManage(projectId));
    }

    @Test
    void assertCanManageAllowsProjectAdmin() {
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "desc", organization);
        UUID projectId = project.getId();
        User admin = new User("padmin@enerscope.org", "P", "Admin", "hashed", PlatformRole.USER);
        authenticateAs(admin);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), admin.getId()))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, admin.getId()))
                .thenReturn(Optional.of(projectMember(admin, project, ProjectMemberType.ADMIN)));

        assertDoesNotThrow(() -> service.assertCanManage(projectId));
    }

    @Test
    void assertCanManageRejectsUnknownProject() {
        UUID projectId = UUID.randomUUID();
        authenticateAs(new User("someone@enerscope.org", "Some", "One", "hashed", PlatformRole.USER));
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.assertCanManage(projectId));
    }

    // ---- resolveProjectIdForVersion -------------------------------------------

    @Test
    void resolveProjectIdForVersionReturnsOwningProjectId() {
        UUID versionId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "desc", new Organization("Acme"));
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        when(projectRepository.findByVersions_Id(versionId)).thenReturn(Optional.of(project));

        assertEquals(project.getId(), service.resolveProjectIdForVersion(versionId));
    }

    @Test
    void resolveProjectIdForVersionRejectsUnknownVersion() {
        UUID versionId = UUID.randomUUID();
        when(projectRepository.findByVersions_Id(versionId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resolveProjectIdForVersion(versionId));
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

    private ProjectMember projectMember(User user, Project project, ProjectMemberType type) {
        ProjectMember member = new ProjectMember(user, project);
        Set<ProjectMemberPermission> permissions = switch (type) {
            case ADMIN -> EnumSet.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                    ProjectMemberPermission.VIEW_PROJECT);
            case EDITOR -> EnumSet.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT);
        };
        member.addRole(new ProjectMemberRole(type.name(), type, permissions));
        return member;
    }
}
