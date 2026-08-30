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
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.user.service.UserService;
import org.enerscope.util.AuthUtil;
import org.springframework.stereotype.Service;

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
    private final AppLogger logger;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrganizationMemberRepository organizationMemberRepository,
                                UserRepository userRepository,
                                UserService userService,
                                AppLogger logger) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.logger = logger;
    }

    /**
     * Organizations visible to the current caller: a platform ADMIN sees every
     * organization; anyone else sees the organizations they are a member of.
     */
    public List<Organization> listForCurrentUser() {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User caller = session.getUser();
        if (caller.getPlatformRole() == PlatformRole.ADMIN) {
            return organizationRepository.findAll();
        }
        return organizationRepository.findDistinctByMembers_User_Id(caller.getId());
    }

    public Organization createOrganization(CreateOrganizationRequestDTO data) {
        Organization organization = new Organization(data.name());
        Organization saved = organizationRepository.save(organization);
        logger.info("Created organization {}", saved.getName());
        return saved;
    }

    public OrganizationMember addMember(UUID organizationId, AddOrganizationMemberRequestDTO data) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
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
                data.mail(), data.firstName(), data.lastName(), data.password(), PlatformRole.USER));

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

    /**
     * Ensures the current caller may create/manage users in the given
     * organization: a platform ADMIN, or an org member holding
     * {@link OrganizationMemberPermission#MANAGE_ORGANIZATION}. Throws
     * {@link UnauthorizedException} (401) if unauthenticated or
     * {@link ForbiddenException} (403) otherwise.
     */
    public void assertCanManageUsers(UUID organizationId) {
        Session session = AuthUtil.currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User caller = session.getUser();
        if (caller.getPlatformRole() == PlatformRole.ADMIN) {
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

    /** Default permission set granted for a member type (see {@link #DEFAULT_PERMISSIONS}). */
    public static Set<OrganizationMemberPermission> defaultPermissionsFor(OrganizationMemberType type) {
        return DEFAULT_PERMISSIONS.get(type);
    }

    private boolean hasManagePermission(OrganizationMember member) {
        return member.getRoles().stream()
                .anyMatch(role -> role.getPermissions().contains(OrganizationMemberPermission.MANAGE_ORGANIZATION));
    }
}
