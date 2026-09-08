package org.enerscope.project.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.dto.CreateProjectRequestDTO;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.version.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        private ProjectService projectService;

        @BeforeEach
        void setUp() {
                projectService = new ProjectService(
                                projectRepository, projectMemberRepository, organizationRepository, userRepository,
                                logger, versionService);
        }

        // ---- createProject -------------------------------------------------------

        @Test
        void createProjectPersistsAndLinksToOrganization() {
                UUID orgId = UUID.randomUUID();
                Organization organization = new Organization("Acme");
                when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
                when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

                Project saved = projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId));

                assertEquals("Grid Expansion", saved.getName());
                assertEquals("Expands the regional grid", saved.getDescription());
                assertEquals(organization, saved.getOrganization());
                assertTrue(organization.getProjects().contains(saved));
        }

        @Test
        void createProjectRejectsUnknownOrganization() {
                UUID orgId = UUID.randomUUID();
                when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class, () -> projectService.createProject(
                                new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid", orgId)));
                verify(projectRepository, never()).save(any());
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
}
