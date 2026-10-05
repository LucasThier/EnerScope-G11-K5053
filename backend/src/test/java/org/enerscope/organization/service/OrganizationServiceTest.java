package org.enerscope.organization.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.dto.OrganizationDTO;
import org.enerscope.organization.dto.UpdateOrganizationMemberRoleRequestDTO;
import org.enerscope.organization.dto.UpdateOrganizationRequestDTO;
import org.enerscope.organization.dto.RegisterOrganizationUserRequestDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.repository.ProjectMemberRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.repository.UserRepository;
import org.enerscope.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private AppLogger logger;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(
                organizationRepository, organizationMemberRepository, userRepository, userService,
                projectMemberRepository, logger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- createOrganization ------------------------------------------------

    @Test
    void createOrganizationPersistsAndReturnsOrganization() {
        authenticateAs(admin());
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization saved = organizationService.createOrganization(new CreateOrganizationRequestDTO("Acme"));

        assertEquals("Acme", saved.getName());
        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void createOrganizationRejectsRegularUserWith403() {
        authenticateAs(new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER));

        assertThrows(ForbiddenException.class,
                () -> organizationService.createOrganization(new CreateOrganizationRequestDTO("Acme")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void createOrganizationRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class,
                () -> organizationService.createOrganization(new CreateOrganizationRequestDTO("Acme")));
        verify(organizationRepository, never()).save(any());
    }

    // ---- addMember -----------------------------------------------------------

    @Test
    void addMemberGrantsOwnerFullPermissions() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
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
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
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
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(userRepository, never()).findById(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberRejectsUnknownUser() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(new Organization("Acme")));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberRejectsDuplicateMembership() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(new Organization("Acme")));
        when(userRepository.findById(userId)).thenReturn(
                Optional.of(new User("jane@enerscope.org", "Jane", "Doe", "hashed")));
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, userId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberAllowsOrganizationOwner() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User caller = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        Organization organization = new Organization("Acme");
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, caller.getId()))
                .thenReturn(Optional.of(ownerMembership(caller, organization)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, userId)).thenReturn(false);
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER));

        assertEquals(user, saved.getUser());
    }

    @Test
    void addMemberRejectsPlainMemberWith403() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User caller = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        Organization organization = new Organization("Acme");
        OrganizationMember membership = new OrganizationMember(caller, organization);
        membership.addRole(new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(), OrganizationMemberType.MEMBER,
                EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, caller.getId()))
                .thenReturn(Optional.of(membership));

        assertThrows(ForbiddenException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER)));
        verify(userRepository, never()).findById(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void addMemberRejectsUnauthenticated() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(new Organization("Acme")));

        assertThrows(UnauthorizedException.class, () -> organizationService.addMember(
                orgId, new AddOrganizationMemberRequestDTO(UUID.randomUUID(), OrganizationMemberType.MEMBER)));
        verify(userRepository, never()).findById(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    // ---- listForCurrentUser --------------------------------------------------

    @Test
    void listForCurrentUserReturnsAllForAdmin() {
        authenticateAs(admin());
        when(organizationRepository.findSummaries())
                .thenReturn(List.of(summary("Acme", 3L), summary("Globex", 0L)));

        assertEquals(2, organizationService.listForCurrentUser().size());
        verify(organizationRepository, never()).findSummariesForMember(any());
    }

    @Test
    void listForCurrentUserReturnsMembershipsForRegularUser() {
        User user = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(user);
        when(organizationRepository.findSummariesForMember(user.getId()))
                .thenReturn(List.of(summary("Mine", 1L)));

        List<OrganizationDTO> result = organizationService.listForCurrentUser();

        assertEquals(1, result.size());
        assertEquals("Mine", result.get(0).name());
        assertEquals(1L, result.get(0).memberCount());
    }

    @Test
    void listForCurrentUserRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> organizationService.listForCurrentUser());
    }

    @Test
    void updateOrganizationRenamesIt() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDTO result = organizationService.updateOrganization(
                organizationId, new UpdateOrganizationRequestDTO("Acme Energy"));

        assertEquals("Acme Energy", result.name());
        assertEquals("Acme Energy", organization.getName());
    }

    @Test
    void updateOrganizationAnswersWithTheRealMemberCount() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        organization.addMember(memberOf(organization, OrganizationMemberType.OWNER));
        organization.addMember(memberOf(organization, OrganizationMemberType.MEMBER));
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationMemberRepository.countByOrganizationId(organizationId)).thenReturn(2L);

        OrganizationDTO result = organizationService.updateOrganization(
                organizationId, new UpdateOrganizationRequestDTO("Acme Energy"));

        assertEquals(2L, result.memberCount());
    }

    @Test
    void updateOrganizationRejectsANullBody() {
        authenticateAs(admin());

        assertThrows(IllegalArgumentException.class,
                () -> organizationService.updateOrganization(UUID.randomUUID(), null));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void updateOrganizationRejectsUnknownOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.updateOrganization(
                organizationId, new UpdateOrganizationRequestDTO("Acme Energy")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void updateOrganizationRejectsAnOrganizationOwnerWith403() {
        User owner = new User("owner@enerscope.org", "Own", "Er", "hashed", PlatformRole.USER);
        authenticateAs(owner);

        assertThrows(ForbiddenException.class, () -> organizationService.updateOrganization(
                UUID.randomUUID(), new UpdateOrganizationRequestDTO("Acme Energy")));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void updateOrganizationRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> organizationService.updateOrganization(
                UUID.randomUUID(), new UpdateOrganizationRequestDTO("Acme Energy")));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void addMemberRejectsADeactivatedOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.addMember(
                organizationId, new AddOrganizationMemberRequestDTO(
                        UUID.randomUUID(), OrganizationMemberType.MEMBER)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void registerUserInOrganizationRejectsADeactivatedOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> organizationService.registerUserInOrganization(organizationId,
                        new RegisterOrganizationUserRequestDTO(
                                "new@enerscope.org", "New", "User", "password123", null)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void removeMemberRejectsADeactivatedOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> organizationService.removeMember(organizationId, UUID.randomUUID()));
        verify(organizationMemberRepository, never()).delete(any());
    }

    @Test
    void listMembersRejectsADeactivatedOrganization() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.existsByIdAndActiveTrue(organizationId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> organizationService.listMembers(organizationId));
        verify(organizationMemberRepository, never()).findByOrganizationIdWithUser(any());
    }

    @Test
    void updateOrganizationRejectsADeactivatedOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.updateOrganization(
                organizationId, new UpdateOrganizationRequestDTO("Acme Energy")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void deactivateOrganizationDeactivatesTheRowOnly() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        Project project = new Project("Grid Expansion", "Expands the grid", organization);
        organization.addProject(project);
        OrganizationMember member = memberOf(organization, OrganizationMemberType.MEMBER);
        organization.addMember(member);
        authenticateAs(admin());
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        organizationService.deactivateOrganization(organizationId);

        assertFalse(organization.isActive());
        assertTrue(project.isActive());
        assertTrue(member.isActive());
        verify(organizationRepository).save(organization);
    }

    @Test
    void deactivateOrganizationIsIdempotent() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        organization.deactivate();
        authenticateAs(admin());
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        organizationService.deactivateOrganization(organizationId);

        assertFalse(organization.isActive());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void deactivateOrganizationRejectsUnknownOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> organizationService.deactivateOrganization(organizationId));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void deactivateOrganizationRejectsANonAdminCallerWith403() {
        authenticateAs(new User("owner@enerscope.org", "Own", "Er", "hashed", PlatformRole.USER));

        assertThrows(ForbiddenException.class,
                () -> organizationService.deactivateOrganization(UUID.randomUUID()));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void deactivateOrganizationRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class,
                () -> organizationService.deactivateOrganization(UUID.randomUUID()));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void reactivateOrganizationBringsItBack() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        organization.deactivate();
        authenticateAs(admin());
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        organizationService.reactivateOrganization(organizationId);

        assertTrue(organization.isActive());
        verify(organizationRepository).save(organization);
    }

    @Test
    void reactivateOrganizationIsIdempotent() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        authenticateAs(admin());
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        organizationService.reactivateOrganization(organizationId);

        assertTrue(organization.isActive());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void reactivateOrganizationRejectsANonAdminCallerWith403() {
        authenticateAs(new User("owner@enerscope.org", "Own", "Er", "hashed", PlatformRole.USER));

        assertThrows(ForbiddenException.class,
                () -> organizationService.reactivateOrganization(UUID.randomUUID()));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void reactivateOrganizationRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class,
                () -> organizationService.reactivateOrganization(UUID.randomUUID()));
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void listOwnedByCurrentUserAsksForTheManagePermission() {
        User owner = new User("owner@enerscope.org", "Own", "Er", "hashed", PlatformRole.USER);
        authenticateAs(owner);
        when(organizationRepository.findOwnedBy(
                owner.getId(), OrganizationMemberPermission.MANAGE_ORGANIZATION))
                .thenReturn(List.of(summary("Acme", 3L)));

        assertEquals(1, organizationService.listOwnedByCurrentUser().size());
    }

    @Test
    void listOwnedByCurrentUserIsEmptyForSomebodyWhoOwnsNothing() {
        User plain = new User("plain@enerscope.org", "Pla", "In", "hashed", PlatformRole.USER);
        authenticateAs(plain);
        when(organizationRepository.findOwnedBy(
                plain.getId(), OrganizationMemberPermission.MANAGE_ORGANIZATION))
                .thenReturn(List.of());

        assertTrue(organizationService.listOwnedByCurrentUser().isEmpty());
    }

    @Test
    void listOwnedByCurrentUserRejectsUnauthenticated() {
        assertThrows(UnauthorizedException.class,
                () -> organizationService.listOwnedByCurrentUser());
        verify(organizationRepository, never()).findOwnedBy(any(), any());
    }

    // ---- registerUserInOrganization -----------------------------------------

    @Test
    void registerUserInOrganizationAllowsPlatformAdmin() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenReturn(new User("new@enerscope.org", "New", "User", "hashed", PlatformRole.USER));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123", null));

        assertEquals("new@enerscope.org", saved.getUser().getMail());
        assertEquals(OrganizationMemberType.MEMBER, saved.getRoles().iterator().next().getMemberType());
    }

    @Test
    void registerUserInOrganizationAllowsOrganizationOwner() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User owner = new User("owner@enerscope.org", "Ow", "Ner", "hashed", PlatformRole.USER);
        authenticateAs(owner);
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, owner.getId()))
                .thenReturn(Optional.of(ownerMembership(owner, organization)));
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenReturn(new User("new@enerscope.org", "New", "User", "hashed", PlatformRole.USER));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember saved = organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123", null));

        assertEquals("new@enerscope.org", saved.getUser().getMail());
    }

    @Test
    void registerUserInOrganizationRejectsNonOwnerMemberWith403() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User plainMember = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(plainMember);
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(orgId, plainMember.getId()))
                .thenReturn(Optional.of(viewOnlyMembership(plainMember, organization)));

        assertThrows(ForbiddenException.class, () -> organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123", null)));
        verify(userService, never()).register(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void registerUserInOrganizationRejectsUnauthenticatedCaller() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(new Organization("Acme")));

        assertThrows(UnauthorizedException.class, () -> organizationService.registerUserInOrganization(
                orgId, new RegisterOrganizationUserRequestDTO("new@enerscope.org", "New", "User", "password123", null)));
        verify(userService, never()).register(any());
    }

    @Test
    void registerUserInOrganizationPropagatesJobTitle() {
        UUID orgId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(new Organization("Acme")));
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenReturn(new User("new@enerscope.org", "New", "User", "hashed", PlatformRole.USER));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        organizationService.registerUserInOrganization(orgId, new RegisterOrganizationUserRequestDTO(
                "new@enerscope.org", "New", "User", "password123", "Senior Investment Analyst"));

        ArgumentCaptor<RegisterRequestDTO> captor = ArgumentCaptor.forClass(RegisterRequestDTO.class);
        verify(userService).register(captor.capture());
        assertEquals("Senior Investment Analyst", captor.getValue().jobTitle());
    }

    // ---- listMembers ---------------------------------------------------------

    @Test
    void listMembersReturnsMembersForPlatformAdmin() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        authenticateAs(admin());
        when(organizationRepository.existsByIdAndActiveTrue(orgId)).thenReturn(true);
        when(organizationMemberRepository.findByOrganizationIdWithUser(orgId)).thenReturn(List.of(
                viewOnlyMembership(new User("a@enerscope.org", "A", "One", "hashed"), organization),
                viewOnlyMembership(new User("b@enerscope.org", "B", "Two", "hashed"), organization)));

        assertEquals(2, organizationService.listMembers(orgId).size());
    }

    @Test
    void listMembersAllowsAnyMemberOfTheOrganization() {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User plainMember = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(plainMember);
        when(organizationRepository.existsByIdAndActiveTrue(orgId)).thenReturn(true);
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, plainMember.getId()))
                .thenReturn(true);
        when(organizationMemberRepository.findByOrganizationIdWithUser(orgId))
                .thenReturn(List.of(viewOnlyMembership(plainMember, organization)));

        assertEquals(1, organizationService.listMembers(orgId).size());
    }

    @Test
    void listMembersRejectsNonMemberWith403() {
        UUID orgId = UUID.randomUUID();
        User outsider = new User("outsider@enerscope.org", "Out", "Sider", "hashed", PlatformRole.USER);
        authenticateAs(outsider);
        when(organizationRepository.existsByIdAndActiveTrue(orgId)).thenReturn(true);
        when(organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, outsider.getId()))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () -> organizationService.listMembers(orgId));
        verify(organizationMemberRepository, never()).findByOrganizationIdWithUser(any());
    }

    @Test
    void listMembersRejectsUnknownOrganization() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.existsByIdAndActiveTrue(orgId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> organizationService.listMembers(orgId));
        verify(organizationMemberRepository, never()).findByOrganizationIdWithUser(any());
    }

    // ---- helpers -------------------------------------------------------------

    private OrganizationDTO summary(String name, long memberCount) {
        return new OrganizationDTO(UUID.randomUUID(), name, Instant.now(), true, memberCount);
    }

    private User admin() {
        return new User("admin@enerscope.org", "Admin", "User", "hashed", PlatformRole.ADMIN);
    }

    @Test
    void removeMemberDeletesTheMembership() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        OrganizationMember member = memberOf(organization, OrganizationMemberType.MEMBER);
        organization.addMember(member);
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByUserInOrganization(member.getUser().getId(), organizationId))
                .thenReturn(List.of());

        organizationService.removeMember(organizationId, memberId);

        verify(organizationMemberRepository).delete(member);
        assertFalse(organization.getMembers().contains(member));
    }

    @Test
    void removeMemberAlsoRemovesTheirProjectMembershipsInThatOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        OrganizationMember member = memberOf(organization, OrganizationMemberType.MEMBER);
        Project project = new Project("Grid Expansion", "Expands the grid", organization);
        ProjectMember projectMembership = new ProjectMember(member.getUser(), project);
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByUserInOrganization(member.getUser().getId(), organizationId))
                .thenReturn(List.of(projectMembership));

        organizationService.removeMember(organizationId, memberId);

        verify(projectMemberRepository).deleteAll(List.of(projectMembership));
        verify(organizationMemberRepository).delete(member);
    }

    @Test
    void removeMemberTouchesNoProjectMembershipWhenTheUserIsInNone() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        OrganizationMember member = memberOf(organization, OrganizationMemberType.MEMBER);
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByUserInOrganization(member.getUser().getId(), organizationId))
                .thenReturn(List.of());

        organizationService.removeMember(organizationId, memberId);

        verify(projectMemberRepository, never()).deleteAll(any());
    }

    @Test
    void removeMemberAllowsAnOwnerToRemoveThemselves() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        User caller = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        Organization organization = new Organization("Acme");
        OrganizationMember own = ownerMembership(caller, organization);
        organization.addMember(own);
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(own));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)).thenReturn(Optional.of(own));
        when(projectMemberRepository.findByUserInOrganization(caller.getId(), organizationId))
                .thenReturn(List.of());

        organizationService.removeMember(organizationId, memberId);

        verify(organizationMemberRepository).delete(own);
    }

    @Test
    void removeMemberAllowsPlatformAdmin() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        OrganizationMember member = memberOf(organization, OrganizationMemberType.MEMBER);
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByUserInOrganization(member.getUser().getId(), organizationId))
                .thenReturn(List.of());

        organizationService.removeMember(organizationId, memberId);

        verify(organizationMemberRepository, never()).findByOrganizationIdAndUserId(any(), any());
        verify(organizationMemberRepository).delete(member);
    }

    @Test
    void removeMemberRejectsAPlainMemberWith403() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        User caller = new User("member@enerscope.org", "Plain", "Member", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        Organization organization = new Organization("Acme");
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(memberOf(organization, OrganizationMemberType.MEMBER)));

        assertThrows(ForbiddenException.class,
                () -> organizationService.removeMember(organizationId, memberId));
        verify(organizationMemberRepository, never()).delete(any());
        verify(projectMemberRepository, never()).deleteAll(any());
    }

    @Test
    void removeMemberRejectsUnauthenticated() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(organizationRepository.findByIdAndActiveTrue(organizationId))
                .thenReturn(Optional.of(new Organization("Acme")));

        assertThrows(UnauthorizedException.class,
                () -> organizationService.removeMember(organizationId, memberId));
        verify(organizationMemberRepository, never()).delete(any());
    }

    @Test
    void removeMemberRejectsUnknownOrganization() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> organizationService.removeMember(organizationId, UUID.randomUUID()));
        verify(organizationMemberRepository, never()).delete(any());
    }

    @Test
    void removeMemberRejectsUnknownMember() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId))
                .thenReturn(Optional.of(new Organization("Acme")));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> organizationService.removeMember(organizationId, memberId));
        assertEquals("Member not found", ex.getMessage());
        verify(organizationMemberRepository, never()).delete(any());
        verify(projectMemberRepository, never()).deleteAll(any());
    }

    @Test
    void removeMemberRejectsAMemberOfAnotherOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId))
                .thenReturn(Optional.of(new Organization("Acme")));
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId))
                .thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> organizationService.removeMember(organizationId, memberId));
        assertEquals("Member not found", ex.getMessage());
        verify(organizationMemberRepository).findByIdAndOrganizationId(memberId, organizationId);
        verify(organizationMemberRepository, never()).delete(any());
        verify(projectMemberRepository, never()).deleteAll(any());
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

    private OrganizationMember memberOf(Organization organization, OrganizationMemberType type) {
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER);
        return type == OrganizationMemberType.OWNER
                ? ownerMembership(user, organization)
                : viewOnlyMembership(user, organization);
    }

    private OrganizationMember viewOnlyMembership(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(), OrganizationMemberType.MEMBER,
                EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        return member;
    }

    private Organization stubActiveOrganization(UUID organizationId) {
        Organization organization = new Organization("Acme");
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.of(organization));
        return organization;
    }

    private OrganizationMember stubMembership(UUID organizationId, UUID memberId, OrganizationMember member) {
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId))
                .thenReturn(Optional.of(member));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));
        return member;
    }

    @Test
    void changeMemberRoleDemotesAnOwnerToMember() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = stubActiveOrganization(organizationId);
        OrganizationMember member = stubMembership(organizationId, memberId,
                memberOf(organization, OrganizationMemberType.OWNER));
        authenticateAs(admin());
        assertEquals(OrganizationMemberType.OWNER, member.getRoles().iterator().next().getMemberType());
        assertTrue(member.getRoles().iterator().next().getPermissions()
                .contains(OrganizationMemberPermission.MANAGE_ORGANIZATION));

        OrganizationMember result = organizationService.changeMemberRole(organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.MEMBER));

        assertEquals(1, result.getRoles().size());
        OrganizationMemberRole role = result.getRoles().iterator().next();
        assertEquals(OrganizationMemberType.MEMBER, role.getMemberType());
        assertEquals(OrganizationMemberType.MEMBER.name(), role.getName());
        assertEquals(EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION), role.getPermissions());
        verify(organizationMemberRepository).save(member);
    }

    @Test
    void changeMemberRolePromotesAMemberToOwner() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = stubActiveOrganization(organizationId);
        OrganizationMember member = stubMembership(organizationId, memberId,
                memberOf(organization, OrganizationMemberType.MEMBER));
        authenticateAs(admin());
        assertEquals(EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION),
                member.getRoles().iterator().next().getPermissions());

        OrganizationMember result = organizationService.changeMemberRole(organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER));

        assertEquals(1, result.getRoles().size());
        OrganizationMemberRole role = result.getRoles().iterator().next();
        assertEquals(OrganizationMemberType.OWNER, role.getMemberType());
        assertEquals(OrganizationMemberType.OWNER.name(), role.getName());
        assertEquals(EnumSet.of(OrganizationMemberPermission.MANAGE_ORGANIZATION,
                OrganizationMemberPermission.VIEW_ORGANIZATION), role.getPermissions());
    }

    @Test
    void changeMemberRoleCollapsesSeveralRolesIntoOne() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = stubActiveOrganization(organizationId);
        OrganizationMember member = memberOf(organization, OrganizationMemberType.OWNER);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(), OrganizationMemberType.MEMBER,
                EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        stubMembership(organizationId, memberId, member);
        authenticateAs(admin());
        assertEquals(2, member.getRoles().size());

        organizationService.changeMemberRole(organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.MEMBER));

        assertEquals(1, member.getRoles().size());
        assertEquals(OrganizationMemberType.MEMBER, member.getRoles().iterator().next().getMemberType());
        assertFalse(member.getRoles().iterator().next().getPermissions()
                .contains(OrganizationMemberPermission.MANAGE_ORGANIZATION));
    }

    @Test
    void changeMemberRoleToTheCurrentRoleIsANoOp() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = stubActiveOrganization(organizationId);
        OrganizationMember member = memberOf(organization, OrganizationMemberType.OWNER);
        when(organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId))
                .thenReturn(Optional.of(member));
        OrganizationMemberRole before = member.getRoles().iterator().next();
        authenticateAs(admin());

        OrganizationMember result = organizationService.changeMemberRole(organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER));

        assertEquals(1, result.getRoles().size());
        assertTrue(result.getRoles().contains(before));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void changeMemberRoleRejectsAPlainMemberWith403() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        User caller = new User("member@enerscope.org", "Mem", "Ber", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        Organization organization = stubActiveOrganization(organizationId);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(viewOnlyMembership(caller, organization)));

        assertThrows(ForbiddenException.class, () -> organizationService.changeMemberRole(
                organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER)));
        verify(organizationMemberRepository, never()).findByIdAndOrganizationId(any(), any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void changeMemberRoleAllowsAnOrganizationOwner() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        User caller = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
        authenticateAs(caller);
        Organization organization = stubActiveOrganization(organizationId);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(ownerMembership(caller, organization)));
        OrganizationMember target = stubMembership(organizationId, memberId,
                memberOf(organization, OrganizationMemberType.MEMBER));

        organizationService.changeMemberRole(organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER));

        assertEquals(OrganizationMemberType.OWNER, target.getRoles().iterator().next().getMemberType());
    }

    @Test
    void changeMemberRoleRejectsUnauthenticated() {
        UUID organizationId = UUID.randomUUID();
        stubActiveOrganization(organizationId);

        assertThrows(UnauthorizedException.class, () -> organizationService.changeMemberRole(
                organizationId, UUID.randomUUID(),
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void changeMemberRoleRejectsAMemberOfAnotherOrganizationWithTheSameMessageAsAMissingOne() {
        UUID organizationId = UUID.randomUUID();
        UUID foreignMemberId = UUID.randomUUID();
        UUID missingMemberId = UUID.randomUUID();
        stubActiveOrganization(organizationId);
        authenticateAs(admin());
        when(organizationMemberRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.empty());

        EntityNotFoundException foreign = assertThrows(EntityNotFoundException.class,
                () -> organizationService.changeMemberRole(organizationId, foreignMemberId,
                        new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER)));
        EntityNotFoundException missing = assertThrows(EntityNotFoundException.class,
                () -> organizationService.changeMemberRole(organizationId, missingMemberId,
                        new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER)));

        assertEquals("Member not found", foreign.getMessage());
        assertEquals(foreign.getMessage(), missing.getMessage());
        verify(organizationMemberRepository).findByIdAndOrganizationId(foreignMemberId, organizationId);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void changeMemberRoleRejectsADeactivatedOrganization() {
        UUID organizationId = UUID.randomUUID();
        authenticateAs(admin());
        when(organizationRepository.findByIdAndActiveTrue(organizationId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> organizationService.changeMemberRole(
                organizationId, UUID.randomUUID(),
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.MEMBER)));
        verify(organizationMemberRepository, never()).findByIdAndOrganizationId(any(), any());
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void changeMemberRoleRejectsAMissingRole() {
        authenticateAs(admin());

        assertThrows(IllegalArgumentException.class, () -> organizationService.changeMemberRole(
                UUID.randomUUID(), UUID.randomUUID(), new UpdateOrganizationMemberRoleRequestDTO(null)));
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void changeMemberRoleLeavesProjectMembershipsAlone() {
        UUID organizationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Organization organization = stubActiveOrganization(organizationId);
        stubMembership(organizationId, memberId, memberOf(organization, OrganizationMemberType.OWNER));
        authenticateAs(admin());

        organizationService.changeMemberRole(organizationId, memberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.MEMBER));

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void demotedOwnerLosesManageUsersAndPromotedMemberGainsIt() {
        UUID organizationId = UUID.randomUUID();
        UUID ownerMemberId = UUID.randomUUID();
        UUID plainMemberId = UUID.randomUUID();
        Organization organization = stubActiveOrganization(organizationId);
        User ownerUser = new User("owner@enerscope.org", "Owner", "User", "hashed", PlatformRole.USER);
        User plainUser = new User("plain@enerscope.org", "Plain", "User", "hashed", PlatformRole.USER);
        ReflectionTestUtils.setField(ownerUser, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(plainUser, "id", UUID.randomUUID());
        OrganizationMember ownerMembership = ownerMembership(ownerUser, organization);
        OrganizationMember plainMembership = viewOnlyMembership(plainUser, organization);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, ownerUser.getId()))
                .thenReturn(Optional.of(ownerMembership));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, plainUser.getId()))
                .thenReturn(Optional.of(plainMembership));
        when(organizationMemberRepository.findByIdAndOrganizationId(ownerMemberId, organizationId))
                .thenReturn(Optional.of(ownerMembership));
        when(organizationMemberRepository.findByIdAndOrganizationId(plainMemberId, organizationId))
                .thenReturn(Optional.of(plainMembership));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAs(ownerUser);
        organizationService.assertCanManageUsers(organizationId);
        assertThrows(ForbiddenException.class, () -> {
            authenticateAs(plainUser);
            organizationService.assertCanManageUsers(organizationId);
        });

        authenticateAs(admin());
        organizationService.changeMemberRole(organizationId, ownerMemberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.MEMBER));
        organizationService.changeMemberRole(organizationId, plainMemberId,
                new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER));

        authenticateAs(ownerUser);
        assertThrows(ForbiddenException.class, () -> organizationService.assertCanManageUsers(organizationId));
        authenticateAs(plainUser);
        organizationService.assertCanManageUsers(organizationId);
    }
}
