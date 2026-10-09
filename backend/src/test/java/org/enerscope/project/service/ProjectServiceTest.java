package org.enerscope.project.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.dto.CreateProjectRequestDTO;
import org.enerscope.project.dto.ProjectMemberCandidateDTO;
import org.enerscope.project.dto.ProjectSummaryDTO;
import org.enerscope.project.dto.UpdateProjectMemberRoleRequestDTO;
import org.enerscope.project.dto.UpdateProjectRequestDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        private OrganizationMemberRepository organizationMemberRepository;
        @Mock
        private UserRepository userRepository;

        @Mock
        private VersionService versionService;
        // Mocked, unlike ProjectAccessGuard: the membership rule it carries is a
        // full service with its own collaborators, and OrganizationServiceTest
        // already covers it. Here we only care that createProject runs it.
        @Mock
        private OrganizationService organizationService;
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
                // The guard is wired as a real collaborator over the same mocked
                // repositories, not as a mock: these tests assert on authorization
                // outcomes, and a mocked guard would only ever prove that the mock
                // was called. ProjectAccessGuardTest covers the rules themselves.
                projectService = new ProjectService(
                                projectRepository, projectMemberRepository, organizationRepository,
                                organizationMemberRepository, userRepository,
                                logger, versionService,
                                new ProjectAccessGuard(projectRepository, projectMemberRepository, logger),
                                organizationService);
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
                when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
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
                when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
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
                when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId)));
                verify(projectRepository, never()).save(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void createProjectChecksCallerBelongsToTargetOrganization() {
                UUID orgId = UUID.randomUUID();
                Organization organization = new Organization("Acme");
                User creator = creator();
                authenticateAs(creator);
                when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
                when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId));

                verify(organizationService).assertIsMemberOf(orgId, "create projects in");
        }

        @Test
        void createProjectRejectsCallerOutsideOrganizationWith403() {
                UUID orgId = UUID.randomUUID();
                User creator = creator();
                authenticateAs(creator);
                when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
                when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(new Organization("Acme")));
                doThrow(new ForbiddenException("You are not allowed to create projects in this organization"))
                                .when(organizationService).assertIsMemberOf(orgId, "create projects in");

                assertThrows(ForbiddenException.class, () -> projectService.createProject(
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
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                stubUserInOrganization(user);
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
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                stubUserInOrganization(user);
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
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.empty());

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
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
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
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                User jane = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                when(userRepository.findById(userId)).thenReturn(Optional.of(jane));
                stubUserInOrganization(jane);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(true);

                assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberAllowsProjectAdmin() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                User caller = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.ADMIN)));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                stubUserInOrganization(user);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(false);
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                ProjectMember saved = projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR));

                assertEquals(user, saved.getUser());
                assertEquals(project, saved.getProject());
        }

        @Test
        void addMemberAllowsPlatformAdmin() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                authenticateAs(admin());
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                stubUserInOrganization(user);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(false);
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                assertEquals(user, projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)).getUser());

                verify(projectMemberRepository, never()).findByProjectIdAndUserId(any(), any());
        }

        @Test
        void addMemberRejectsProjectEditorWith403() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                User caller = new User("editor@enerscope.org", "Ed", "Itor", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.EDITOR)));

                assertThrows(ForbiddenException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
                verify(userRepository, never()).findById(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsCallerWhoIsNotAMemberWith403() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                User caller = new User("outsider@enerscope.org", "Out", "Sider", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.empty());

                assertThrows(ForbiddenException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.ADMIN)));
                verify(userRepository, never()).findById(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsUnauthenticated() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));

                assertThrows(UnauthorizedException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.ADMIN)));
                verify(userRepository, never()).findById(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void updateProjectChangesNameAndDescription() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.ADMIN)));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

                Project saved = projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("Grid Expansion II", "A wider grid"));

                assertEquals("Grid Expansion II", saved.getName());
                assertEquals("A wider grid", saved.getDescription());
        }

        @Test
        void updateProjectLeavesOutTheFieldsThatAreNull() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

                Project saved = projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("Grid Expansion II", null));

                assertEquals("Grid Expansion II", saved.getName());
                assertEquals("Expands the regional grid", saved.getDescription());
        }

        @Test
        void updateProjectRejectsAPatchWithEveryFieldNull() {
                UUID projectId = UUID.randomUUID();

                assertThrows(IllegalArgumentException.class, () -> projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO(null, null)));
                verify(projectRepository, never()).findById(any());
                verify(projectRepository, never()).save(any());
        }

        @Test
        void updateProjectRejectsANullBody() {
                UUID projectId = UUID.randomUUID();

                assertThrows(IllegalArgumentException.class, () -> projectService.updateProject(projectId, null));
                verify(projectRepository, never()).findById(any());
        }

        @Test
        void updateProjectRejectsABlankName() {
                UUID projectId = UUID.randomUUID();

                assertThrows(IllegalArgumentException.class, () -> projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("   ", null)));
                verify(projectRepository, never()).save(any());
        }

        @Test
        void updateProjectRejectsABlankDescription() {
                UUID projectId = UUID.randomUUID();

                assertThrows(IllegalArgumentException.class, () -> projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO(null, "   ")));
                verify(projectRepository, never()).save(any());
        }

        @Test
        void updateProjectRejectsUnknownProject() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("Grid Expansion II", null)));
                verify(projectRepository, never()).save(any());
        }

        @Test
        void updateProjectAllowsPlatformAdmin() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

                assertEquals("Grid Expansion II", projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("Grid Expansion II", null)).getName());

                verify(projectMemberRepository, never()).findByProjectIdAndUserId(any(), any());
        }

        @Test
        void updateProjectRejectsProjectEditorWith403() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("editor@enerscope.org", "Ed", "Itor", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.EDITOR)));

                assertThrows(ForbiddenException.class, () -> projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("Grid Expansion II", null)));
                assertEquals("Grid Expansion", project.getName());
                verify(projectRepository, never()).save(any());
        }

        @Test
        void updateProjectRejectsUnauthenticated() {
                UUID projectId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

                assertThrows(UnauthorizedException.class, () -> projectService.updateProject(
                                projectId, new UpdateProjectRequestDTO("Grid Expansion II", null)));
                verify(projectRepository, never()).save(any());
        }

        @Test
        void createProjectRejectsADeactivatedOrganization() {
                UUID orgId = UUID.randomUUID();
                authenticateAs(creator());
                when(userRepository.findById(any())).thenReturn(Optional.of(creator()));
                when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands it", orgId)));
                verify(projectRepository, never()).save(any());
        }

        // ---- listMembers ---------------------------------------------------------

        @Test
        void listMembersReturnsEveryMemberForPlatformAdmin() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(true);
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
                when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(true);
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
                when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(true);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, caller.getId())).thenReturn(false);

                assertThrows(ForbiddenException.class, () -> projectService.listMembers(projectId));
                verify(projectMemberRepository, never()).findByProjectIdWithUser(any());
        }

        @Test
        void listMembersRejectsUnauthenticated() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(true);

                assertThrows(UnauthorizedException.class, () -> projectService.listMembers(projectId));
                verify(projectMemberRepository, never()).findByProjectIdWithUser(any());
        }

        @Test
        void listMembersRejectsUnknownProject() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(false);

                assertThrows(IllegalArgumentException.class, () -> projectService.listMembers(projectId));
                verify(projectMemberRepository, never()).findByProjectIdWithUser(any());
        }

        // ---- listForCurrentUser --------------------------------------------------

        @Test
        void listForCurrentUserReturnsEveryProjectForAdmin() {
                User admin = admin();
                authenticateAs(admin);
                when(projectRepository.findSummaries(admin.getId(), null))
                                .thenReturn(List.of(summary("Grid Expansion", 3)));

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
                verify(projectRepository, never()).findSummaries(any(), any());
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
                authenticateAs(admin());
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

        @Test
        void saveVersionRejectsCallerWithoutEditPermissionWith403() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("viewer@enerscope.org", "View", "Er", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.empty());

                assertThrows(ForbiddenException.class,
                                () -> projectService.saveVersion(projectId, new VersionDTO("Baseline", null)));
                verify(versionService, never()).saveVersion(any());
                verify(projectRepository, never()).save(any());
        }

        @Test
        void saveVersionRejectsUnauthenticated() {
                UUID projectId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

                assertThrows(UnauthorizedException.class,
                                () -> projectService.saveVersion(projectId, new VersionDTO("Baseline", null)));
                verify(versionService, never()).saveVersion(any());
                verify(projectRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsInactiveProjectBeforeLookingUpTheUser() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.empty());

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(UUID.randomUUID(), ProjectMemberType.EDITOR)));

                assertEquals("Project not found", ex.getMessage());
                verify(userRepository, never()).findById(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsAnInactiveUser() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                user.deactivate();
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));

                assertEquals("User is not active", ex.getMessage());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsAUserOutsideTheProjectsOrganizationEvenForAPlatformAdmin() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(organizationMemberRepository.findByOrganizationIdAndUserId(any(), any()))
                                .thenReturn(Optional.empty());

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));

                assertEquals("User is not a member of the project's organization", ex.getMessage());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberRejectsAUserWhoseOrganizationMembershipIsInactive() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Organization organization = new Organization("Acme");
                Project project = new Project("Grid Expansion", "Expands the regional grid", organization);
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                OrganizationMember orgMember = new OrganizationMember(user, organization);
                orgMember.deactivate();
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(organizationMemberRepository.findByOrganizationIdAndUserId(any(), any()))
                                .thenReturn(Optional.of(orgMember));

                assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void addMemberCannotReAddAUserWhoseProjectMembershipIsInactive() {
                UUID projectId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
                ProjectMember inactiveMembership = memberOf(user, project, ProjectMemberType.EDITOR);
                inactiveMembership.deactivate();
                assertFalse(inactiveMembership.isActive());
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                stubUserInOrganization(user);
                when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(true);

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> projectService.addMember(
                                projectId, new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR)));

                assertEquals("User is already a member of this project", ex.getMessage());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void changeMemberRoleDemotesAnAdminReplacingTheWholeRole() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(2L);
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));
                assertEquals(ProjectMemberType.ADMIN, member.getRoles().iterator().next().getMemberType());
                assertTrue(member.getRoles().iterator().next().getPermissions()
                                .contains(ProjectMemberPermission.MANAGE_PROJECT));

                ProjectMember saved = projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR));

                assertEquals(1, saved.getRoles().size());
                ProjectMemberRole role = saved.getRoles().iterator().next();
                assertEquals(ProjectMemberType.EDITOR, role.getMemberType());
                assertEquals("EDITOR", role.getName());
                assertEquals(Set.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT),
                                role.getPermissions());
                verify(projectMemberRepository).save(member);
        }

        @Test
        void changeMemberRolePromotesAnEditorWithoutConsultingTheAdminCount() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("john@enerscope.org"), project, ProjectMemberType.EDITOR);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));
                assertEquals(ProjectMemberType.EDITOR, member.getRoles().iterator().next().getMemberType());

                ProjectMember saved = projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.ADMIN));

                ProjectMemberRole role = saved.getRoles().iterator().next();
                assertEquals(ProjectMemberType.ADMIN, role.getMemberType());
                assertEquals(Set.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                                ProjectMemberPermission.VIEW_PROJECT), role.getPermissions());
                verify(projectMemberRepository, never()).countAdminsByProject(any());
        }

        @Test
        void changeMemberRoleIsANoOpWhenTheRoleDoesNotChange() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                ProjectMemberRole original = member.getRoles().iterator().next();
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));

                ProjectMember result = projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.ADMIN));

                assertSame(member, result);
                assertSame(original, result.getRoles().iterator().next());
                verify(projectMemberRepository, never()).save(any());
                verify(projectMemberRepository, never()).countAdminsByProject(any());
        }

        @Test
        void changeMemberRoleRefusesToDemoteTheLastActiveAdmin() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(1L);
                assertEquals(ProjectMemberType.ADMIN, member.getRoles().iterator().next().getMemberType());

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> projectService
                                .changeMemberRole(projectId, memberId,
                                                new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR)));

                assertEquals("The project would be left without an administrator", ex.getMessage());
                assertEquals(ProjectMemberType.ADMIN, member.getRoles().iterator().next().getMemberType());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void changeMemberRoleAllowsDemotingAnAdminWhenAnotherOneRemains() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(2L);
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR));

                verify(projectMemberRepository).save(member);
        }

        @Test
        void changeMemberRoleDoesNotApplyTheGuardToAnAdminThatNoLongerCounts() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                member.deactivate();
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR));

                verify(projectMemberRepository, never()).countAdminsByProject(any());
                verify(projectMemberRepository).save(member);
        }

        @Test
        void changeMemberRoleDoesNotApplyTheGuardToAnAdminWhoseUserIsInactive() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                User deactivated = userNamed("jane@enerscope.org");
                deactivated.deactivate();
                ProjectMember member = memberOf(deactivated, project, ProjectMemberType.ADMIN);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR));

                verify(projectMemberRepository, never()).countAdminsByProject(any());
                verify(projectMemberRepository).save(member);
        }

        @Test
        void changeMemberRoleAllowsAProjectAdminCaller() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                User caller = userNamed("owner@enerscope.org");
                authenticateAs(caller);
                ProjectMember member = memberOf(userNamed("john@enerscope.org"), project, ProjectMemberType.EDITOR);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.ADMIN)));
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

                ProjectMember saved = projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.ADMIN));

                assertEquals(ProjectMemberType.ADMIN, saved.getRoles().iterator().next().getMemberType());
        }

        @Test
        void changeMemberRoleRejectsAProjectEditorCallerWith403() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                User caller = userNamed("editor@enerscope.org");
                authenticateAs(caller);
                activeProject(projectId);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.EDITOR)));

                assertThrows(ForbiddenException.class, () -> projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.ADMIN)));
                verify(projectMemberRepository, never()).findByIdAndProjectId(any(), any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void changeMemberRoleRejectsAnOrganizationOwnerWhoIsNotOnTheProjectWith403() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                User caller = userNamed("orgowner@enerscope.org");
                authenticateAs(caller);
                activeProject(projectId);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.empty());

                assertThrows(ForbiddenException.class, () -> projectService.changeMemberRole(
                                projectId, memberId, new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.ADMIN)));
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void changeMemberRoleAnswers404ForAMemberOfAnotherProject() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                activeProject(projectId);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.empty());

                EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> projectService
                                .changeMemberRole(projectId, memberId,
                                                new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR)));

                assertEquals("Member not found", ex.getMessage());
                verify(projectMemberRepository).findByIdAndProjectId(memberId, projectId);
                verify(projectMemberRepository, never()).findById(any());
                verify(projectMemberRepository, never()).save(any());
        }

        @Test
        void changeMemberRoleRejectsAnInactiveProject() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.empty());

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> projectService
                                .changeMemberRole(projectId, UUID.randomUUID(),
                                                new UpdateProjectMemberRoleRequestDTO(ProjectMemberType.EDITOR)));

                assertEquals("Project not found", ex.getMessage());
                verify(projectMemberRepository, never()).findByIdAndProjectId(any(), any());
        }

        @Test
        void changeMemberRoleRejectsAMissingMemberType() {
                assertThrows(IllegalArgumentException.class, () -> projectService.changeMemberRole(
                                UUID.randomUUID(), UUID.randomUUID(), new UpdateProjectMemberRoleRequestDTO(null)));
                assertThrows(IllegalArgumentException.class, () -> projectService.changeMemberRole(
                                UUID.randomUUID(), UUID.randomUUID(), null));
        }

        @Test
        void removeMemberDeletesTheMembershipAndDetachesItFromTheProject() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("john@enerscope.org"), project, ProjectMemberType.EDITOR);
                project.addMember(member);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                assertTrue(project.getMembers().contains(member));

                projectService.removeMember(projectId, memberId);

                assertFalse(project.getMembers().contains(member));
                verify(projectMemberRepository).delete(member);
                verify(projectMemberRepository, never()).countAdminsByProject(any());
        }

        @Test
        void removeMemberRefusesToRemoveTheLastActiveAdmin() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                project.addMember(member);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(1L);
                assertTrue(project.getMembers().contains(member));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> projectService.removeMember(projectId, memberId));

                assertEquals("The project would be left without an administrator", ex.getMessage());
                assertTrue(project.getMembers().contains(member));
                verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
        }

        @Test
        void removeMemberAllowsRemovingAnAdminWhenAnotherOneRemains() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                ProjectMember member = memberOf(userNamed("jane@enerscope.org"), project, ProjectMemberType.ADMIN);
                project.addMember(member);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(member));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(2L);

                projectService.removeMember(projectId, memberId);

                assertFalse(project.getMembers().contains(member));
                verify(projectMemberRepository).delete(member);
        }

        @Test
        void removeMemberRefusesTheSelfRemovalOfTheLastAdmin() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                User caller = userNamed("owner@enerscope.org");
                ProjectMember own = memberOf(caller, project, ProjectMemberType.ADMIN);
                project.addMember(own);
                authenticateAs(caller);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(own));
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(own));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(1L);

                assertThrows(IllegalArgumentException.class, () -> projectService.removeMember(projectId, memberId));

                assertTrue(project.getMembers().contains(own));
                verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
        }

        @Test
        void removeMemberAllowsAnAdminToLeaveWhenAnotherOneRemains() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                User caller = userNamed("owner@enerscope.org");
                ProjectMember own = memberOf(caller, project, ProjectMemberType.ADMIN);
                project.addMember(own);
                authenticateAs(caller);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(own));
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.of(own));
                when(projectMemberRepository.countAdminsByProject(projectId)).thenReturn(2L);

                projectService.removeMember(projectId, memberId);

                assertFalse(project.getMembers().contains(own));
                verify(projectMemberRepository).delete(own);
        }

        @Test
        void removeMemberRejectsAProjectEditorCallerWith403EvenForTheirOwnMembership() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                Project project = activeProject(projectId);
                User caller = userNamed("editor@enerscope.org");
                ProjectMember own = memberOf(caller, project, ProjectMemberType.EDITOR);
                project.addMember(own);
                authenticateAs(caller);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(own));

                assertThrows(ForbiddenException.class, () -> projectService.removeMember(projectId, memberId));

                assertTrue(project.getMembers().contains(own));
                verify(projectMemberRepository, never()).findByIdAndProjectId(any(), any());
                verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
        }

        @Test
        void removeMemberAnswers404ForAMemberOfAnotherProject() {
                UUID projectId = UUID.randomUUID();
                UUID memberId = UUID.randomUUID();
                activeProject(projectId);
                authenticateAs(admin());
                when(projectMemberRepository.findByIdAndProjectId(memberId, projectId)).thenReturn(Optional.empty());

                EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                                () -> projectService.removeMember(projectId, memberId));

                assertEquals("Member not found", ex.getMessage());
                verify(projectMemberRepository).findByIdAndProjectId(memberId, projectId);
                verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
        }

        @Test
        void removeMemberRejectsAnInactiveProject() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class,
                                () -> projectService.removeMember(projectId, UUID.randomUUID()));
                verify(projectMemberRepository, never()).findByIdAndProjectId(any(), any());
                verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
        }

        @Test
        void listMemberCandidatesQueriesTheProjectsOrganizationWithAMatchAllPatternWhenQIsBlank() {
                UUID projectId = UUID.randomUUID();
                Project project = activeProject(projectId);
                List<ProjectMemberCandidateDTO> expected = List.of(
                                new ProjectMemberCandidateDTO(UUID.randomUUID(), "Jane", "Doe", "jane@enerscope.org"));
                authenticateAs(admin());
                when(organizationMemberRepository.findProjectMemberCandidates(
                                project.getOrganization().getId(), projectId, "%")).thenReturn(expected);

                assertEquals(expected, projectService.listMemberCandidates(projectId, null));
                assertEquals(expected, projectService.listMemberCandidates(projectId, "   "));
                verify(organizationMemberRepository, times(2)).findProjectMemberCandidates(
                                project.getOrganization().getId(), projectId, "%");
        }

        @Test
        void listMemberCandidatesLowercasesTrimsAndEscapesTheSearchTerm() {
                UUID projectId = UUID.randomUUID();
                Project project = activeProject(projectId);
                authenticateAs(admin());
                when(organizationMemberRepository.findProjectMemberCandidates(any(), eq(projectId), any()))
                                .thenReturn(List.of());

                projectService.listMemberCandidates(projectId, "  Ja_N%\\x ");

                verify(organizationMemberRepository).findProjectMemberCandidates(
                                project.getOrganization().getId(), projectId, "%ja\\_n\\%\\\\x%");
        }

        @Test
        void listMemberCandidatesRejectsAProjectEditorWith403() {
                UUID projectId = UUID.randomUUID();
                User caller = userNamed("editor@enerscope.org");
                authenticateAs(caller);
                activeProject(projectId);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.EDITOR)));

                assertThrows(ForbiddenException.class, () -> projectService.listMemberCandidates(projectId, null));
                verify(organizationMemberRepository, never()).findProjectMemberCandidates(any(), any(), any());
        }

        @Test
        void listMemberCandidatesAllowsAProjectAdmin() {
                UUID projectId = UUID.randomUUID();
                User caller = userNamed("owner@enerscope.org");
                authenticateAs(caller);
                Project project = activeProject(projectId);
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.ADMIN)));
                when(organizationMemberRepository.findProjectMemberCandidates(
                                project.getOrganization().getId(), projectId, "%")).thenReturn(List.of());

                assertNotNull(projectService.listMemberCandidates(projectId, null));
        }

        @Test
        void listMemberCandidatesRejectsAnInactiveProject() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.listMemberCandidates(projectId, null));
                verify(organizationMemberRepository, never()).findProjectMemberCandidates(any(), any(), any());
        }

        // ---- helpers -------------------------------------------------------------

        private Project activeProject(UUID projectId) {
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findByIdAndActiveTrue(projectId)).thenReturn(Optional.of(project));
                return project;
        }

        private User userNamed(String mail) {
                return new User(mail, "First", "Last", "hashed", PlatformRole.USER);
        }

        private ProjectMember memberOf(User user, Project project, ProjectMemberType memberType) {
                ProjectMember member = new ProjectMember(user, project);
                member.addRole(new ProjectMemberRole(
                                memberType.name(), memberType, DEFAULT_PERMISSIONS_FOR_TEST.get(memberType)));
                return member;
        }

        private void stubUserInOrganization(User user) {
                when(organizationMemberRepository.findByOrganizationIdAndUserId(any(), any()))
                                .thenReturn(Optional.of(new OrganizationMember(user, new Organization("Acme"))));
        }

        private User admin() {
                return new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
        }

        @Test
        void deactivateProjectDeactivatesTheProject() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.ADMIN)));

                projectService.deactivateProject(projectId);

                assertFalse(project.isActive());
                verify(projectRepository).save(project);
        }

        @Test
        void deactivateProjectCascadesToMembersAndVersions() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                ProjectMember member = new ProjectMember(
                                new User("jane@enerscope.org", "Jane", "Doe", "hashed"), project);
                project.addMember(member);
                Version version = new Version("v1", null, null, null, null, null);
                Version child = new Version("v2", version, null, null, null, null);
                project.addVersion(version);
                project.addVersion(child);
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

                projectService.deactivateProject(projectId);

                assertFalse(project.isActive());
                assertFalse(member.isActive());
                assertFalse(version.isActive());
                assertFalse(child.isActive());
        }

        @Test
        void deactivateProjectIsIdempotent() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                project.deactivate();
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

                projectService.deactivateProject(projectId);

                assertFalse(project.isActive());
                verify(projectRepository, never()).save(any());
        }

        @Test
        void deactivateProjectRejectsUnknownProject() {
                UUID projectId = UUID.randomUUID();
                when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.deactivateProject(projectId));
                verify(projectRepository, never()).save(any());
        }

        @Test
        void deactivateProjectAllowsPlatformAdmin() {
                UUID projectId = UUID.randomUUID();
                authenticateAs(admin());
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

                projectService.deactivateProject(projectId);

                assertFalse(project.isActive());
                verify(projectMemberRepository, never()).findByProjectIdAndUserId(any(), any());
        }

        @Test
        void deactivateProjectRejectsProjectEditorWith403() {
                UUID projectId = UUID.randomUUID();
                User caller = new User("editor@enerscope.org", "Ed", "Itor", "hashed", PlatformRole.USER);
                authenticateAs(caller);
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
                when(projectMemberRepository.findByProjectIdAndUserId(projectId, caller.getId()))
                                .thenReturn(Optional.of(sampleMember(ProjectMemberType.EDITOR)));

                assertThrows(ForbiddenException.class, () -> projectService.deactivateProject(projectId));
                assertTrue(project.isActive());
                verify(projectRepository, never()).save(any());
        }

        @Test
        void deactivateProjectRejectsUnauthenticated() {
                UUID projectId = UUID.randomUUID();
                Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
                when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

                assertThrows(UnauthorizedException.class, () -> projectService.deactivateProject(projectId));
                assertTrue(project.isActive());
                verify(projectRepository, never()).save(any());
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
                                memberCount, Instant.now(), null);
        }
}
