package org.enerscope.organization.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.dto.RegisterOrganizationUserRequestDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.user.service.UserService;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrganizationService {

    // Default permission set granted per member type when a member is added.
    // There is no API to customize permissions yet; when that becomes needed,
    // extend AddOrganizationMemberRequestDTO instead of this map.
    private static final Map<OrganizationMemberType, Set<OrganizationMemberPermission>> DEFAULT_PERMISSIONS = Map.of(
            OrganizationMemberType.OWNER, EnumSet.of(
                    OrganizationMemberPermission.MANAGE_ORGANIZATION,
                    OrganizationMemberPermission.VIEW_ORGANIZATION),
            OrganizationMemberType.MEMBER, EnumSet.of(
                    OrganizationMemberPermission.VIEW_ORGANIZATION)
    );

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ProjectMemberRepository projectMemberRepository;
    private final AppLogger logger;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrganizationMemberRepository organizationMemberRepository,
                                UserRepository userRepository,
                                UserService userService,
                                ProjectMemberRepository projectMemberRepository,
                                AppLogger logger) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.projectMemberRepository = projectMemberRepository;
        this.logger = logger;
    }

    /**
     * Organizations visible to the current caller: a platform ADMIN sees every
     * organization; anyone else sees the organizations they are a member of.
     */
    public List<Organization> listForCurrentUser() {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return organizationRepository.findAll();
        }
        return organizationRepository.findDistinctByMembers_User_Id(caller.getId());
    }

    /**
     * Members of an organization, for the team screens. Readable by any member
     * of that organization — seeing who else has access is not a management
     * action, unlike {@link #assertCanManageUsers(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new IllegalArgumentException("Organization not found");
        }
        assertCanViewOrganization(organizationId);
        return organizationMemberRepository.findByOrganizationIdWithUser(organizationId);
    }

    /**
     * Creates an organization. Restricted to platform ADMINs: an organization is
     * an administrative container created <em>for</em> someone else, which is
     * also why the creator is deliberately not enrolled in it. This mirrors the
     * frontend, where {@code /admin/organizations} already sits behind an
     * ADMIN-only route.
     */
    public Organization createOrganization(CreateOrganizationRequestDTO data) {
        AuthUtil.requirePlatformAdmin(logger, "create organizations");
        Organization organization = new Organization(data.name());
        Organization saved = organizationRepository.save(organization);
        logger.info("Created organization {}", saved.getName());
        return saved;
    }

    /**
     * Adds an existing user to an organization. Restricted to the same callers
     * as {@link #registerUserInOrganization}: a platform ADMIN or a member
     * holding {@link OrganizationMemberPermission#MANAGE_ORGANIZATION}. The check
     * runs after the organization is resolved, so an unknown id keeps answering
     * {@code IllegalArgumentException} (400), and before the user lookup, so an
     * unauthorized caller cannot probe which user ids exist.
     */
    public OrganizationMember addMember(UUID organizationId, AddOrganizationMemberRequestDTO data) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        assertCanManageUsers(organizationId);
        User user = userRepository.findById(data.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, data.userId())) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }

        OrganizationMember member = new OrganizationMember(user, organization);
        OrganizationMemberRole role = new OrganizationMemberRole(
                data.memberType().name(), data.memberType(), DEFAULT_PERMISSIONS.get(data.memberType()));
        member.addRole(role);
        organization.addMember(member);

        OrganizationMember saved = organizationMemberRepository.save(member);
        logger.info("Added user {} to organization {} as {}", user.getMail(), organization.getName(), data.memberType());
        return saved;
    }

    /**
     * Registers a brand new platform user and adds them to the organization as a
     * MEMBER. Only a platform ADMIN, or an existing organization member holding
     * the {@link OrganizationMemberPermission#MANAGE_ORGANIZATION} permission,
     * may call this.
     */
    public OrganizationMember registerUserInOrganization(UUID organizationId,
                                                         RegisterOrganizationUserRequestDTO data) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        assertCanManageUsers(organizationId);

        // Organization-registered accounts are always regular platform users.
        User user = userService.register(new RegisterRequestDTO(
                data.mail(), data.firstName(), data.lastName(), data.password(), PlatformRole.USER,
                data.jobTitle()));

        OrganizationMember member = new OrganizationMember(user, organization);
        OrganizationMemberRole role = new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(),
                OrganizationMemberType.MEMBER,
                DEFAULT_PERMISSIONS.get(OrganizationMemberType.MEMBER));
        member.addRole(role);
        organization.addMember(member);

        OrganizationMember saved = organizationMemberRepository.save(member);
        logger.info("Registered user {} into organization {}", user.getMail(), organization.getName());
        return saved;
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID memberId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        assertCanManageUsers(organizationId);

        OrganizationMember member = organizationMemberRepository
                .findByIdAndOrganizationId(memberId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        User user = member.getUser();
        List<ProjectMember> projectMemberships =
                projectMemberRepository.findByUserInOrganization(user.getId(), organizationId);
        if (!projectMemberships.isEmpty()) {
            projectMemberRepository.deleteAll(projectMemberships);
        }

        organization.removeMember(member);
        organizationMemberRepository.delete(member);

        logger.info("Removed user {} from organization {} along with {} project memberships",
                user.getMail(), organization.getName(), projectMemberships.size());
    }

    /**
     * Ensures the current caller may create/manage users in the given
     * organization: a platform ADMIN, or an org member holding
     * {@link OrganizationMemberPermission#MANAGE_ORGANIZATION}. Throws
     * {@link UnauthorizedException} (401) if unauthenticated or
     * {@link ForbiddenException} (403) otherwise.
     */
    public void assertCanManageUsers(UUID organizationId) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return; // platform admins can manage any organization
        }
        boolean canManage = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, caller.getId())
                .map(this::hasManagePermission)
                .orElse(false);
        if (!canManage) {
            throw new ForbiddenException("You are not allowed to manage users in this organization");
        }
    }

    /**
     * Ensures the current caller may read the given organization: a platform
     * ADMIN, or any of its members regardless of permissions.
     */
    public void assertCanViewOrganization(UUID organizationId) {
        assertIsMemberOf(organizationId, "view");
    }

    /**
     * Ensures the current caller belongs to the organization — a platform ADMIN,
     * or any of its members regardless of permissions — for an action named by
     * {@code action}, which is what the caller reads back in the 403 message.
     * Plain membership is the bar here; {@link #assertCanManageUsers} is the
     * stricter check for administrative actions.
     */
    public void assertIsMemberOf(UUID organizationId, String action) {
        User caller = AuthUtil.requireSession().getUser();
        if (AuthUtil.isPlatformAdmin(caller)) {
            return;
        }
        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, caller.getId())) {
            logger.warn("User {} is not allowed to {} organization {}", caller.getMail(), action, organizationId);
            throw new ForbiddenException("You are not allowed to " + action + " this organization");
        }
    }

    /** Default permission set granted for a member type (see {@link #DEFAULT_PERMISSIONS}). */
    public static Set<OrganizationMemberPermission> defaultPermissionsFor(OrganizationMemberType type) {
        return DEFAULT_PERMISSIONS.get(type);
    }

    private boolean hasManagePermission(OrganizationMember member) {
        return member.getRoles().stream()
                .anyMatch(role -> role.getPermissions().contains(OrganizationMemberPermission.MANAGE_ORGANIZATION));
    }
}
