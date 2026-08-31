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
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectService {

        // Default permission set granted per member type when a member is added.
        // There is no API to customize permissions yet; when that becomes needed,
        // extend AddProjectMemberRequestDTO instead of this map.
        private static final Map<ProjectMemberType, Set<ProjectMemberPermission>> DEFAULT_PERMISSIONS = Map.of(
                        ProjectMemberType.ADMIN, EnumSet.of(
                                        ProjectMemberPermission.MANAGE_PROJECT,
                                        ProjectMemberPermission.EDIT_PROJECT,
                                        ProjectMemberPermission.VIEW_PROJECT),
                        ProjectMemberType.EDITOR, EnumSet.of(
                                        ProjectMemberPermission.EDIT_PROJECT,
                                        ProjectMemberPermission.VIEW_PROJECT));

        private final ProjectRepository projectRepository;
        private final ProjectMemberRepository projectMemberRepository;
        private final OrganizationRepository organizationRepository;
        private final UserRepository userRepository;
        private final AppLogger logger;
        private final VersionService versionService;

        public ProjectService(ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        OrganizationRepository organizationRepository,
                        UserRepository userRepository,
                        AppLogger logger, VersionService versionService) {
                this.projectRepository = projectRepository;
                this.projectMemberRepository = projectMemberRepository;
                this.organizationRepository = organizationRepository;
                this.userRepository = userRepository;
                this.logger = logger;
                this.versionService = versionService;
        }

        public Project createProject(CreateProjectRequestDTO data) {
                Organization organization = organizationRepository.findById(data.organizationId())
                                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

                Project project = new Project(data.name(), data.description(), organization);
                organization.addProject(project);

                Project saved = projectRepository.save(project);
                logger.info("Created project {} in organization {}", saved.getName(), organization.getName());
                return saved;
        }

        public ProjectMember addMember(UUID projectId, AddProjectMemberRequestDTO data) {
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                User user = userRepository.findById(data.userId())
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));
                if (projectMemberRepository.existsByProjectIdAndUserId(projectId, data.userId())) {
                        throw new IllegalArgumentException("User is already a member of this project");
                }

                ProjectMember member = new ProjectMember(user, project);
                ProjectMemberRole role = new ProjectMemberRole(
                                data.memberType().name(), data.memberType(),
                                DEFAULT_PERMISSIONS.get(data.memberType()));
                member.addRole(role);
                project.addMember(member);

                ProjectMember saved = projectMemberRepository.save(member);
                logger.info("Added user {} to project {} as {}", user.getMail(), project.getName(), data.memberType());
                return saved;
        }

        public Version saveVersion(UUID projectId, VersionDTO versionDTO) {

                if (projectId == null || versionDTO == null) {
                        throw new IllegalArgumentException("data cannot be null");
                }

                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

                Version version = versionService.saveVersion(versionDTO);

                project.addVersion(version);
                projectRepository.save(project);

                throw new UnsupportedOperationException("Unimplemented method 'saveVersion'");
        }
}
