package org.enerscope.project.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.dto.CreateProjectRequestDTO;
import org.enerscope.project.dto.ProjectSummaryDTO;
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
import org.enerscope.user.repository.UserRepository;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

        @Mock
        private ProjectRepository projectRepository;
        @Mock
        private ProjectMemberRepository projectMemberRepository;
        @Mock
        private OrganizationRepository organizationRepository;
        @Mock
        private UserRepository userRepository;

        @Mock
        private VersionService versionService;
        @Mock
        private AppLogger logger;

        private static final java.util.Map<ProjectMemberType, Set<ProjectMemberPermission>>
                        DEFAULT_PERMISSIONS_FOR_TEST = java.util.Map.of(
                                        ProjectMemberType.ADMIN, Set.of(ProjectMemberPermission.MANAGE_PROJECT,
                                                        ProjectMemberPermission.EDIT_PROJECT,
                                                        ProjectMemberPermission.VIEW_PROJECT),
                                        ProjectMemberType.EDITOR, Set.of(ProjectMemberPermission.EDIT_PROJECT,
                                                        ProjectMemberPermission.VIEW_PROJECT));

        private ProjectService projectService;

        @BeforeEach
        void setUp() {
                projectService = new ProjectService(
                                projectRepository, projectMemberRepository, organizationRepository, userRepository,
                                logger, versionService);
        }

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        // ---- createProject -------------------------------------------------------

        @Test
        void createProjectPersistsAndLinksToOrganization() {
                UUID orgId = UUID.randomUUID();
                Organization organization = new Organization("Acme");
                User creator = creator();
                authenticateAs(creator);
                when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
                when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                Project saved = projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId));

                assertEquals("Grid Expansion", saved.getName());
                assertEquals("Expands the regional grid", saved.getDescription());
                assertEquals(organization, saved.getOrganization());
                assertTrue(organization.getProjects().contains(saved));
        }

        @Test
        void createProjectAddsCreatorAsProjectAdmin() {
                UUID orgId = UUID.randomUUID();
                Organization organization = new Organization("Acme");
                User creator = creator();
                authenticateAs(creator);
                when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
                when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                Project saved = projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId));

                assertEquals(1, saved.getMembers().size());
                ProjectMember member = saved.getMembers().get(0);
                assertEquals(creator, member.getUser());
                assertEquals(saved, member.getProject());
                assertEquals(1, member.getRoles().size());
                ProjectMemberRole role = member.getRoles().iterator().next();
                assertEquals(ProjectMemberType.ADMIN, role.getMemberType());
                assertEquals(Set.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                                ProjectMemberPermission.VIEW_PROJECT), role.getPermissions());
        }

        @Test
        void createProjectRejectsUnauthenticated() {
                assertThrows(UnauthorizedException.class, () -> projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid",
                                                UUID.randomUUID())));
                verify(projectRepository, never()).save(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void createProjectRejectsUnknownOrganization() {
                UUID orgId = UUID.randomUUID();
                User creator = creator();
                authenticateAs(creator);
                when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
                when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId)));
                verify(projectRepository, never()).save(any());
                verify(projectMemberRepository, never()).save(any());
        }

        // ---- addMember -------------------------------------------------------

        @Test
        void addMemberGrantsAdminFullPermissions() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(false);
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                ProjectMember saved = projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.ADMIN));

                assertEquals(user, saved.getUser());
                assertEquals(project, saved.getProject());
                assertEquals(1, saved.getRoles().size());
                ProjectMemberRole role = saved.getRoles().iterator().next();
                assertEquals(ProjectMemberType.ADMIN, role.getMemberType());
                assertEquals(Set.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                                ProjectMemberPermission.VIEW_PROJECT), role.getPermissions());
        }

        @Test
        void addMemberGrantsEditorEditAndViewPermissions() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("john@enerscope.org", "John", "Roe", "hashed");
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(false);
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                ProjectMember saved = projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR));

                ProjectMemberRole role = saved.getRoles().iterator().next();
                assertEquals(Set.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT),
                                role.getPermissions());
        }

        @Test
        void addMemberRejectsUnknownProject() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
                verify(userRepository, never()).findById(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsUnknownUser() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsDuplicateMembership() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(
                                Optional.of(new User("jane@enerscope.org", "Jane", "Doe", "hashed")));
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(true);

                assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
                verify(projectMemberRepository, never()).save(any());
        }

        // ---- listMembers ---------------------------------------------------------

        @Test
        void listMembersReturnsEveryMemberForPlatformAdmin() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                when(projectRepository.existsById(projectId)).thenReturn(true);
                when(projectMemberRepository.findByProjectIdWithUser(projectId))
                                .thenReturn(List.of(sampleMember(ProjectMemberType.ADMIN)));

                List<ProjectMember> result = projectService.listMembers(projectId);

                assertEquals(1, result.size());
                assertEquals("jane@enerscope.org", result.get(0).getUser().getMail());
                verify(projectMemberRepository, never()).existsByProjectIdAndUserId(any(), any());
        }

        @Test
        void listMembersReturnsMembersForProjectMember() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                when(projectRepository.existsById(projectId)).thenReturn(true);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(true);
                when(projectMemberRepository.findByProjectIdWithUser(projectId))
                                .thenReturn(List.of(sampleMember(ProjectMemberType.EDITOR)));

                assertEquals(1, projectService.listMembers(projectId).size());
        }

        @Test
        void listMembersRejectsCallerWhoIsNotAMember() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("outsider@enerscope.org", "Out", "Sider", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                when(projectRepository.existsById(projectId)).thenReturn(true);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(false);

                assertThrows(ForbiddenException.class, () -> projectService.listMembers(projectId));
                verify(projectMemberRepository, never()).findByProjectIdWithUser(any());
        }

        @Test
        void listMembersRejectsUnauthenticated() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.existsById(projectId)).thenReturn(true);

                assertThrows(UnauthorizedException.class, () -> projectService.listMembers(projectId));
                verify(projectMemberRepository, never()).findByProjectIdWithUser(any());
        }

        @Test
        void listMembersRejectsUnknownProject() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.existsById(projectId)).thenReturn(false);

                assertThrows(IllegalArgumentException.class, () -> projectService.listMembers(projectId));
                verify(projectMemberRepository, never()).findByProjectIdWithUser(any());
        }

        // ---- listForCurrentUser --------------------------------------------------

        @Test
        void listForCurrentUserReturnsEveryProjectForAdmin() {
                authenticateAs(admin());
                when(projectRepository.findSummaries(null)).thenReturn(List.of(summary("Grid Expansion", 3)));

                List<ProjectSummaryDTO> result = projectService.listForCurrentUser(null);

                assertEquals(1, result.size());
                assertEquals("Grid Expansion", result.get(0).name());
                verify(projectRepository, never()).findSummariesForMember(any(), any());
        }

        @Test
        void listForCurrentUserReturnsOnlyMembershipsForRegularUser() {
                User user = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
                authenticateAs(user);
                when(projectRepository.findSummariesForMember(user.getId(), null))
                                .thenReturn(List.of(summary("Mine", 1)));

                List<ProjectSummaryDTO> result = projectService.listForCurrentUser(null);

                assertEquals(1, result.size());
                assertEquals("Mine", result.get(0).name());
                verify(projectRepository, never()).findSummaries(any());
        }

        @Test
        void listForCurrentUserPassesOrganizationFilterThrough() {
                UUID orgId = UUID.randomUUID();
                User user = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
                authenticateAs(user);
                when(projectRepository.findSummariesForMember(user.getId(), orgId)).thenReturn(List.of());

                assertTrue(projectService.listForCurrentUser(orgId).isEmpty());
                verify(projectRepository).findSummariesForMember(user.getId(), orgId);
        }

        @Test
        void listForCurrentUserRejectsUnauthenticated() {
                assertThrows(UnauthorizedException.class, () -> projectService.listForCurrentUser(null));
        }

        // ---- saveVersion ---------------------------------------------------------

        @Test
        void saveVersionReturnsVersionLinkedToProject() {
                UUID projectId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                Version version = new Version();
                version.setName("Baseline");
                VersionDTO data = new VersionDTO("Baseline", null);
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(versionService.saveVersion(data)).thenReturn(version);

                Version saved = projectService.saveVersion(projectId, data);

                assertEquals(version, saved);
                assertTrue(project.getVersions().contains(version));
                verify(projectRepository).save(project);
        }

        @Test
        void saveVersionRejectsNullProjectId() {
                assertThrows(IllegalArgumentException.class,
                                () -> projectService.saveVersion(null, new VersionDTO("Baseline", null)));
                verify(versionService, never()).saveVersion(any());
        }

        @Test
        void saveVersionRejectsNullVersionData() {
                assertThrows(IllegalArgumentException.class,
                                () -> projectService.saveVersion(UUID.randomUUID(), null));
                verify(versionService, never()).saveVersion(any());
        }

        @Test
        void saveVersionRejectsUnknownProject() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class,
                                () -> projectService.saveVersion(projectId, new VersionDTO("Baseline", null)));
                verify(versionService, never()).saveVersion(any());
                verify(projectRepository, never()).save(any());
        }

        // ---- helpers -------------------------------------------------------------

        private User admin() {
                return new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        }

        private User creator() {
                return new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
        }

        private ProjectMember sampleMember(ProjectMemberType memberType) {
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                ProjectMember member = new ProjectMember(user, project);
                member.addRole(new ProjectMemberRole(
                                memberType.name(), memberType, DEFAULT_PERMISSIONS_FOR_TEST.get(memberType)));
                return member;
        }

        private void authenticateAs(User caller) {
                Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
                var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
                auth.setDetails(session);
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        private ProjectSummaryDTO summary(String name, long memberCount) {
                return new ProjectSummaryDTO(UUID.randomUUID(), name, "A project", UUID.randomUUID(), "Acme",
                                memberCount, Instant.now());
        }
}
