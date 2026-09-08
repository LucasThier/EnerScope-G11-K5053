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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private AppLogger logger;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(
                organizationRepository, organizationMemberRepository, userRepository, userService, logger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- createOrganization ------------------------------------------------

    @Test
    void createOrganizationPersistsAndReturnsOrganization() {
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization saved = organizationService.createOrganization(new CreateOrganizationRequestDTO("Acme"));

        assertEquals("Acme", saved.getName());
        verify(organizationRepository).save(any(Organization.class));
    }

    // ---- addMember -----------------------------------------------------------

    @Test
    void addMemberGrantsOwnerFullPermissions() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, userId)).thenReturn(false);
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.OWNER));

        assertEquals(user, saved.getUser());
        assertEquals(organization, saved.getOrganization());
        assertEquals(1, saved.getRoles().size());
        OrganizationMemberRole role = saved.getRoles().iterator().next();
        assertEquals(OrganizationMemberType.OWNER, role.getMemberType());
        assertTrue(role.getPermissions().containsAll(
                java.util.Set.of(OrganizationMemberPermission.MANAGE_ORGANIZATION,
                        OrganizationMemberPermission.VIEW_ORGANIZATION)));
    }

    @Test
    void addMemberGrantsMemberViewOnlyPermission() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User user = new User("john@enerscope.org", "John", "Roe", "hashed");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, userId)).thenReturn(false);
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER));

        OrganizationMemberRole role = saved.getRoles().iterator().next();
        assertEquals(java.util.Set.of(OrganizationMemberPermission.VIEW_ORGANIZATION), role.getPermissions());
    }

    @Test
    void addMemberRejectsUnknownOrganization() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(userRepository, never()).findById(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberRejectsUnknownUser() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new Organization("Acme")));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberRejectsDuplicateMembership() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new Organization("Acme")));
        when(userRepository.findById(userId)).thenReturn(
                Optional.of(new User("jane@enerscope.org", "Jane", "Doe", "hashed")));
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, userId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(organizationMemberRepository, never()).save(any());
    }

    // ---- listForCurrentUser --------------------------------------------------

    @Test
    void listForCurrentUserReturnsAllForAdmin() {
        authenticateAs(admin());
        when(organizationRepository.findAll())
                .thenReturn(List.of(new Organization("Acme"), new Organization("Globex")));

        assertEquals(2, organizationService.listForCurrentUser().size());
    }

    @Test
    void listForCurrentUserReturnsMembershipsForRegularUser() {
        User user = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(user);
        when(organizationRepository.findDistinctByMembers_User_Id(user.getId()))
                .thenReturn(List.of(new Organization("Mine")));

        List<Organization> result = organizationService.listForCurrentUser();

        assertEquals(1, result.size());
        assertEquals("Mine", result.get(0).getName());
    }

    @Test
    void listForCurrentUserRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> organizationService.listForCurrentUser());
    }

    // ---- registerUserInOrganization -----------------------------------------

    @Test
    void registerUserInOrganizationAllowsPlatformAdmin() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        authenticateAs(admin());
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenReturn(new User("new@enerscope.org", "New", "User", "hashed", PlatformRole.USER));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123"));

        assertEquals("new@enerscope.org", saved.getUser().getMail());
        assertEquals(OrganizationMemberType.MEMBER, saved.getRoles().iterator().next().getMemberType());
    }

    @Test
    void registerUserInOrganizationAllowsOrganizationOwner() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User owner = new User("owner@enerscope.org", "Ow", "Ner", "hashed", PlatformRole.USER);
        authenticateAs(owner);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, owner.getId()))
                .thenReturn(Optional.of(ownerMembership(owner, organization)));
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenReturn(new User("new@enerscope.org", "New", "User", "hashed", PlatformRole.USER));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123"));

        assertEquals("new@enerscope.org", saved.getUser().getMail());
    }

    @Test
    void registerUserInOrganizationRejectsNonOwnerMemberWith403() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User plainMember = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(plainMember);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, plainMember.getId()))
                .thenReturn(Optional.of(viewOnlyMembership(plainMember, organization)));

        assertThrows(ForbiddenException.class, () -> organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123")));
        verify(userService, never()).register(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void registerUserInOrganizationRejectsUnauthenticatedCaller() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new Organization("Acme")));

        assertThrows(UnauthorizedException.class, () -> organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123")));
        verify(userService, never()).register(any());
    }

    // ---- helpers -------------------------------------------------------------

    private User admin() {
        return new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
    }

    private void authenticateAs(User caller) {
        Session session = new Session("token", caller, Instant.now().plusSeconds(3600));
        var auth = new UsernamePasswordAuthenticationToken(caller, null, List.of());
        auth.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private OrganizationMember ownerMembership(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.OWNER.name(), OrganizationMemberType.OWNER,
                EnumSet.of(OrganizationMemberPermission.MANAGE_ORGANIZATION,
                        OrganizationMemberPermission.VIEW_ORGANIZATION)));
        return member;
    }

    private OrganizationMember viewOnlyMembership(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(), OrganizationMemberType.MEMBER,
                EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        return member;
    }
}
