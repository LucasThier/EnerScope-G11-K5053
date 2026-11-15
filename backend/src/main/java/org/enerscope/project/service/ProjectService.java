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
import org.enerscope.project.model.enums.ProjectMemberPermission;
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
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.repository.VersionMemberRepository;
import org.enerscope.version.service.VersionAccessGuard;
import org.enerscope.version.service.VersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        private final VersionMemberRepository versionMemberRepository;
        private final VersionAccessGuard versionAccessGuard;

        public ProjectService(ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        OrganizationRepository organizationRepository,
                        OrganizationMemberRepository organizationMemberRepository,
                        UserRepository userRepository,
                        AppLogger logger, VersionService versionService,
                        ProjectAccessGuard accessGuard,
                        OrganizationService organizationService,
                        VersionMemberRepository versionMemberRepository,
                        VersionAccessGuard versionAccessGuard) {
                this.projectRepository = projectRepository;
                this.projectMemberRepository = projectMemberRepository;
                this.organizationRepository = organizationRepository;
                this.organizationMemberRepository = organizationMemberRepository;
                this.userRepository = userRepository;
                this.logger = logger;
                this.versionService = versionService;
                this.accessGuard = accessGuard;
                this.organizationService = organizationService;
                this.versionMemberRepository = versionMemberRepository;
                this.versionAccessGuard = versionAccessGuard;
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

                Project project = new Project(data.name(), data.description() == null ? "" : data.description().trim(), organization);
                organization.addProject(project);

                Project saved = projectRepository.save(project);
                attachMember(saved, creator, ProjectMemberType.ADMIN);
                logger.info("Created project {} in organization {} with {} as project admin",
                                saved.getName(), organization.getName(), creator.getMail());
                return saved;
        }

        @Transactional
        public Project updateProject(UUID projectId, UpdateProjectRequestDTO data) {
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);

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

                project.updateDetails(data.name(), data.description() == null ? null : data.description().trim());
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
                List<VersionMember> staleVersionMemberships = versionMemberRepository
                                .findByUserIdAndProjectIdIn(data.userId(), List.of(projectId));
                if (!staleVersionMemberships.isEmpty()) {
                        versionMemberRepository.deleteAll(staleVersionMemberships);
                }

                ProjectMember saved = attachMember(project, user, data.memberType());
                logger.info("Added user {} to project {} as {}", user.getMail(), project.getName(), data.memberType());
                return saved;
        }

        @Transactional
        public ProjectMember changeMemberRole(UUID projectId, UUID memberId,
                        UpdateProjectMemberRoleRequestDTO data) {
                projectRepository.findByIdAndActiveTrue(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                accessGuard.assertCanManageProject(projectId);
                if (data == null || data.memberType() == null) {
                        throw new IllegalArgumentException("memberType cannot be null");
                }

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
                List<VersionMember> versionMemberships = versionMemberRepository
                                .findByUserIdAndProjectIdIn(member.getUser().getId(), List.of(projectId));
                if (!versionMemberships.isEmpty()) {
                        versionMemberRepository.deleteAll(versionMemberships);
                }
                project.removeMember(member);
                projectMemberRepository.delete(member);
                logger.info("Removed user {} from project {} along with {} version memberships",
                                member.getUser().getMail(), project.getName(), versionMemberships.size());
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
                UUID parentId = versionDTO.getParentVersion();
                if (parentId == null) {
                        accessGuard.assertCanManageProject(projectId);
                } else {
                        accessGuard.assertCanEditProject(projectId);
                        versionAccessGuard.assertCanEditVersion(parentId);
                        if (project.getVersions().stream().noneMatch(v -> v.isActive() && parentId.equals(v.getId()))) {
                                throw new IllegalArgumentException("Parent version must belong to the selected project");
                        }
                }
                Version version = versionService.saveVersion(versionDTO);

                project.addVersion(version);
                projectRepository.save(project);
                if (parentId != null) {
                        copyVersionRoles(projectId, parentId, version);
                }

                logger.info("Created version {} in project {}", version.getName(), project.getName());
                return version;
        }

        private void copyVersionRoles(UUID projectId, UUID parentId, Version child) {
                List<VersionMember> copies = new ArrayList<>();
                for (VersionMember member : versionMemberRepository.findByVersionIdWithUser(parentId)) {
                        VersionMember copy = new VersionMember(member.getUser(), child);
                        for (VersionMemberRole role : member.getRoles()) {
                                copy.addRole(new VersionMemberRole(role.getName(), role.getMemberType(),
                                                new HashSet<>(role.getPermissions()), role.getVertical()));
                        }
                        copies.add(copy);
                }
                grantCreatorAdmin(projectId, child, copies);
                if (!copies.isEmpty()) {
                        versionMemberRepository.saveAll(copies);
                }
                logger.info("Copied {} version member(s) from version {} to version {}",
                                copies.size(), parentId, child.getName());
        }

        private void grantCreatorAdmin(UUID projectId, Version child, List<VersionMember> copies) {
                User caller = AuthUtil.requireSession().getUser();
                if (AuthUtil.isPlatformAdmin(caller)) {
                        return;
                }
                Optional<ProjectMember> membership = projectMemberRepository
                                .findByProjectIdAndUserId(projectId, caller.getId())
                                .filter(ProjectAccessGuard::isActive);
                if (membership.isEmpty()
                                || ProjectAccessGuard.grants(membership.get(), ProjectMemberPermission.MANAGE_PROJECT)) {
                        return;
                }
                VersionMember creator = copies.stream()
                                .filter(copy -> Objects.equals(copy.getUser().getId(), caller.getId()))
                                .findFirst()
                                .orElseGet(() -> {
                                        VersionMember created = new VersionMember(membership.get().getUser(), child);
                                        copies.add(created);
                                        return created;
                                });
                boolean alreadyAdmin = creator.getRoles().stream()
                                .anyMatch(role -> role.getMemberType() == VersionMemberType.ADMIN);
                if (!alreadyAdmin) {
                        creator.addRole(new VersionMemberRole(VersionMemberType.ADMIN.name(), VersionMemberType.ADMIN,
                                        new HashSet<>(VersionMember.defaultPermissionsFor(VersionMemberType.ADMIN)), null));
                        logger.info("Granted ADMIN on version {} to its creator {}", child.getName(), caller.getMail());
                }
        }

}
