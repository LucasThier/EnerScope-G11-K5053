package org.enerscope.project.service;

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
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.util.AuthUtil;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.dto.VersionSummaryDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

        private final ProjectRepository projectRepository;
        private final ProjectMemberRepository projectMemberRepository;
        private final OrganizationRepository organizationRepository;
        private final OrganizationMemberRepository organizationMemberRepository;
        private final UserRepository userRepository;
        private final AppLogger logger;
        private final VersionService versionService;
        private final ProjectAccessGuard accessGuard;
        private final OrganizationService organizationService;

        public ProjectService(ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        OrganizationRepository organizationRepository,
                        OrganizationMemberRepository organizationMemberRepository,
                        UserRepository userRepository,
                        AppLogger logger, VersionService versionService,
                        ProjectAccessGuard accessGuard,
                        OrganizationService organizationService) {
                this.projectRepository = projectRepository;
                this.projectMemberRepository = projectMemberRepository;
                this.organizationRepository = organizationRepository;
                this.organizationMemberRepository = organizationMemberRepository;
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
                        return projectRepository.findSummaries(caller.getId(), organizationId);
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

                Organization organization = organizationRepository.findByIdAndActiveTrue(data.organizationId())
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

        @Transactional
        public Project updateProject(UUID projectId, UpdateProjectRequestDTO data) {
                if (data == null) {
                        throw new IllegalArgumentException("data cannot be null");
                }
                if (data.name() == null && data.description() == null) {
                        throw new IllegalArgumentException(
                                        "At least one of name or description must be provided");
                }
                if (data.name() != null && data.name().isBlank()) {
                        throw new IllegalArgumentException("Project name cannot be blank");
                }
                if (data.description() != null && data.description().isBlank()) {
                        throw new IllegalArgumentException("Project description cannot be blank");
                }

                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);

                project.updateDetails(data.name(), data.description());
                Project saved = projectRepository.save(project);
                logger.info("Updated project {}", saved.getName());
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
                if (!projectRepository.existsByIdAndActiveTrue(projectId)) {
                        throw new IllegalArgumentException("Project not found");
                }
                assertCanViewProject(projectId);
                return projectMemberRepository.findByProjectIdWithUser(projectId);
        }

        @Transactional
        public void deactivateProject(UUID projectId) {
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);

                if (!project.isActive()) {
                        logger.debug("Project {} is already inactive", project.getName());
                        return;
                }

                project.deactivate();
                project.getMembers().forEach(ProjectMember::deactivate);
                project.getVersions().forEach(Version::deactivate);
                projectRepository.save(project);

                logger.info("Deactivated project {} along with {} members and {} versions",
                                project.getName(), project.getMembers().size(), project.getVersions().size());
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
        @Transactional
        public ProjectMember addMember(UUID projectId, AddProjectMemberRequestDTO data) {
                Project project = projectRepository.findByIdAndActiveTrue(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);
                User user = userRepository.findById(data.userId())
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));
                if (!user.isActive()) {
                        throw new IllegalArgumentException("User is not active");
                }
                UUID organizationId = project.getOrganization().getId();
                boolean inOrganization = organizationMemberRepository
                                .findByOrganizationIdAndUserId(organizationId, user.getId())
                                .filter(OrganizationMember::isActive)
                                .isPresent();
                if (!inOrganization) {
                        throw new IllegalArgumentException("User is not a member of the project's organization");
                }
                if (projectMemberRepository.existsByProjectIdAndUserId(projectId, data.userId())) {
                        throw new IllegalArgumentException("User is already a member of this project");
                }

                ProjectMember saved = attachMember(project, user, data.memberType());
                logger.info("Added user {} to project {} as {}", user.getMail(), project.getName(), data.memberType());
                return saved;
        }

        @Transactional
        public ProjectMember changeMemberRole(UUID projectId, UUID memberId,
                        UpdateProjectMemberRoleRequestDTO data) {
                if (data == null || data.memberType() == null) {
                        throw new IllegalArgumentException("memberType cannot be null");
                }
                projectRepository.findByIdAndActiveTrue(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);

                ProjectMember member = projectMemberRepository.findByIdAndProjectId(memberId, projectId)
                                .orElseThrow(() -> new EntityNotFoundException("Member not found"));

                ProjectMemberType newType = data.memberType();
                boolean alreadyThatRole = member.getRoles().size() == 1
                                && member.getRoles().iterator().next().getMemberType() == newType;
                if (alreadyThatRole) {
                        logger.debug("Member {} already has the {} role; nothing to change", memberId, newType);
                        return member;
                }

                if (newType != ProjectMemberType.ADMIN) {
                        assertNotLastAdmin(projectId, member);
                }
                member.changeRole(newType, ProjectMember.defaultPermissionsFor(newType));
                ProjectMember saved = projectMemberRepository.save(member);
                logger.info("Changed the role of user {} in project {} to {}",
                                saved.getUser().getMail(), saved.getProject().getName(), newType);
                return saved;
        }

        @Transactional
        public void removeMember(UUID projectId, UUID memberId) {
                Project project = projectRepository.findByIdAndActiveTrue(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);

                ProjectMember member = projectMemberRepository.findByIdAndProjectId(memberId, projectId)
                                .orElseThrow(() -> new EntityNotFoundException("Member not found"));

                assertNotLastAdmin(projectId, member);
                project.removeMember(member);
                projectMemberRepository.delete(member);
                logger.info("Removed user {} from project {}", member.getUser().getMail(), project.getName());
        }

        @Transactional(readOnly = true)
        public List<ProjectMemberCandidateDTO> listMemberCandidates(UUID projectId, String q) {
                Project project = projectRepository.findByIdAndActiveTrue(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);
                return organizationMemberRepository.findProjectMemberCandidates(
                                project.getOrganization().getId(), projectId, searchPattern(q));
        }

        private static String searchPattern(String q) {
                if (q == null || q.isBlank()) {
                        return "%";
                }
                String escaped = q.trim().toLowerCase()
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_");
                return "%" + escaped + "%";
        }

        private void assertNotLastAdmin(UUID projectId, ProjectMember member) {
                boolean countsAsAdmin = member.isActive()
                                && member.getUser().isActive()
                                && member.getRoles().stream().anyMatch(r -> r.getMemberType() == ProjectMemberType.ADMIN);
                if (countsAsAdmin && projectMemberRepository.countAdminsByProject(projectId) <= 1) {
                        throw new IllegalArgumentException("The project would be left without an administrator");
                }
        }

        private ProjectMember attachMember(Project project, User user, ProjectMemberType memberType) {
                ProjectMember member = new ProjectMember(user, project);
                ProjectMemberRole role = new ProjectMemberRole(
                                memberType.name(), memberType, ProjectMember.defaultPermissionsFor(memberType));
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

        /**
         * Lists the versions of a project as lightweight summaries. Runs in a
         * read-only transaction so the lazy {@code versions} association is
         * initialised before it is mapped.
         */
        @Transactional(readOnly = true)
        public List<VersionSummaryDTO> listVersions(UUID projectId) {
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

                return project.getVersions().stream()
                                .map(v -> new VersionSummaryDTO(
                                                v.getId(),
                                                v.getName(),
                                                v.getParentVersion() == null ? null : v.getParentVersion().getId(),
                                                v.getCreatedAt()))
                                .toList();
        }
}
