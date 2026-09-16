package org.enerscope.project.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.organization.service.OrganizationService;
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
        private final ProjectAccessGuard accessGuard;
        private final OrganizationService organizationService;

        public ProjectService(ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        OrganizationRepository organizationRepository,
                        UserRepository userRepository,
                        AppLogger logger, VersionService versionService,
                        ProjectAccessGuard accessGuard,
                        OrganizationService organizationService) {
                this.projectRepository = projectRepository;
                this.projectMemberRepository = projectMemberRepository;
                this.organizationRepository = organizationRepository;
                this.userRepository = userRepository;
                this.logger = logger;
                this.versionService = versionService;
                this.accessGuard = accessGuard;
                this.organizationService = organizationService;
        }

        /**
         * Projects visible to the current caller: a platform ADMIN sees every
         * project; anyone else sees the ones they are a member of. Organization
         * membership alone does not grant visibility — a user has to be on the
         * project. {@code organizationId} is an optional extra filter.
         */
        @Transactional(readOnly = true)
        public List<ProjectSummaryDTO> listForCurrentUser(UUID organizationId) {
                User caller = AuthUtil.requireSession().getUser();
                if (AuthUtil.isPlatformAdmin(caller)) {
                        return projectRepository.findSummaries(organizationId);
                }
                return projectRepository.findSummariesForMember(caller.getId(), organizationId);
        }

        /**
         * Creates a project inside an organization, with the caller as its first
         * member and project ADMIN.
         *
         * <p>The caller has to belong to the target organization: without that
         * check any authenticated user could plant a project inside any
         * organization, which is a cross-tenant write. Plain membership is the
         * bar, not {@code MANAGE_ORGANIZATION} — a project is created by the
         * people who are going to work on it, unlike the organization itself. The
         * check runs after the organization is resolved so an unknown id keeps
         * answering {@code IllegalArgumentException} (400).</p>
         */
        @Transactional
        public Project createProject(CreateProjectRequestDTO data) {
                Session session = AuthUtil.requireSession();
                User creator = userRepository.findById(session.getUser().getId())
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));

                Organization organization = organizationRepository.findById(data.organizationId())
                                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
                organizationService.assertIsMemberOf(data.organizationId(), "create projects in");

                Project project = new Project(data.name(), data.description(), organization);
                organization.addProject(project);

                Project saved = projectRepository.save(project);
                attachMember(saved, creator, ProjectMemberType.ADMIN);
                logger.info("Created project {} in organization {} with {} as project admin",
                                saved.getName(), organization.getName(), creator.getMail());
                return saved;
        }

        /**
         * The members of a project, with their user and roles already fetched.
         * Readable by a platform ADMIN or by any member of the project — listing
         * who has access is not a management action, mirroring
         * {@code OrganizationService.assertCanViewOrganization}.
         */
        @Transactional(readOnly = true)
        public List<ProjectMember> listMembers(UUID projectId) {
                if (!projectRepository.existsById(projectId)) {
                        throw new IllegalArgumentException("Project not found");
                }
                assertCanViewProject(projectId);
                return projectMemberRepository.findByProjectIdWithUser(projectId);
        }

        /**
         * Ensures the current caller may read the given project: a platform ADMIN,
         * or any of its members regardless of permissions. The rule itself lives
         * in {@link ProjectAccessGuard}, shared with {@code VersionService}; this
         * stays as the service-level entry point callers already use.
         */
        public void assertCanViewProject(UUID projectId) {
                accessGuard.assertCanViewProject(projectId);
        }

        /**
         * Adds a user to a project. Restricted to callers holding
         * {@code MANAGE_PROJECT} on that project (its ADMIN members) or a platform
         * ADMIN: without the check, any authenticated user could add themselves to
         * any project and, since membership is what grants read access, walk
         * straight into it.
         *
         * <p>The permission check runs after the project is resolved, so an
         * unknown id keeps answering {@code IllegalArgumentException} (400) as it
         * did before, and before the user lookup, so an unauthorized caller cannot
         * probe which user ids exist.</p>
         */
        public ProjectMember addMember(UUID projectId, AddProjectMemberRequestDTO data) {
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);
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
                accessGuard.assertCanEditProject(projectId);

                Version version = versionService.saveVersion(versionDTO);

                project.addVersion(version);
                projectRepository.save(project);

                logger.info("Created version {} in project {}", version.getName(), project.getName());
                return version;
        }
}
