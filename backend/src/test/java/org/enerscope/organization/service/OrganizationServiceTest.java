package org.enerscope.organization.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private AppLogger logger;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(
                organizationRepository, organizationMemberRepository, userRepository, logger);
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
}
