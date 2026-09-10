package org.enerscope.project.service;

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
import org.enerscope.util.AuthUtil;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
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

        /**
         * Projects visible to the current caller: a platform ADMIN sees every
         * project; anyone else sees the ones they are a member of. Organization
         * membership alone does not grant visibility — a user has to be on the
         * project. {@code organizationId} is an optional extra filter.
         */
        @Transactional(readOnly = true)
        public List<ProjectSummaryDTO> listForCurrentUser(UUID organizationId) {
                Session session = AuthUtil.currentSession();
                if (session == null) {
                        throw new UnauthorizedException("Authentication required");
                }
                User caller = session.getUser();
                if (caller.getPlatformRole() == PlatformRole.ADMIN) {
                        return projectRepository.findSummaries(organizationId);
                }
                return projectRepository.findSummariesForMember(caller.getId(), organizationId);
        }

        @Transactional
        public Project createProject(CreateProjectRequestDTO data) {
                Session session = AuthUtil.currentSession();
                if (session == null) {
                        throw new UnauthorizedException("Authentication required");
                }
                User creator = userRepository.findById(session.getUser().getId())
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));

                Organization organization = organizationRepository.findById(data.organizationId())
                                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

                Project project = new Project(data.name(), data.description(), organization);
                organization.addProject(project);

                Project saved = projectRepository.save(project);
                attachMember(saved, creator, ProjectMemberType.ADMIN);
                logger.info("Created project {} in organization {} with {} as project admin",
                                saved.getName(), organization.getName(), creator.getMail());
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

                ProjectMember saved = attachMember(project, user, data.memberType());
                logger.info("Added user {} to project {} as {}", user.getMail(), project.getName(), data.memberType());
                return saved;
        }

        private ProjectMember attachMember(Project project, User user, ProjectMemberType memberType) {
                ProjectMember member = new ProjectMember(user, project);
                ProjectMemberRole role = new ProjectMemberRole(
                                memberType.name(), memberType, DEFAULT_PERMISSIONS.get(memberType));
                member.addRole(role);
                project.addMember(member);
                return projectMemberRepository.save(member);
        }

        /**
         * Creates a version through {@link VersionService} and attaches it to the
         * project. Transactional so the version row and the project link are
         * written as one unit: without it the version would survive a later
         * failure as an orphan, unreachable from any project.
         */
        @Transactional
        public Version saveVersion(UUID projectId, VersionDTO versionDTO) {

                if (projectId == null || versionDTO == null) {
                        throw new IllegalArgumentException("data cannot be null");
                }

                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

                Version version = versionService.saveVersion(versionDTO);

                project.addVersion(version);
                projectRepository.save(project);

                logger.info("Created version {} in project {}", version.getName(), project.getName());
                return version;
        }
}
