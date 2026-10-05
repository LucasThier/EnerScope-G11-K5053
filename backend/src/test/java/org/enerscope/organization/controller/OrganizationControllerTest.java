package org.enerscope.organization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.BulkRegistrationResultDTO;
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
import org.enerscope.organization.service.OrganizationBulkRegistrationService;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link OrganizationController}. Uses {@code @WebMvcTest}
 * (web layer only, no database) with the real {@link SecurityConfig}/
 * {@link AuthFilter} so every request goes through the actual JWT filter
 * chain, like {@code AuthControllerTest}. {@link OrganizationService} is
 * mocked.
 */
@WebMvcTest(OrganizationController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class OrganizationControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrganizationService organizationService;
    @MockitoBean
    private OrganizationBulkRegistrationService bulkRegistrationService;
    @MockitoBean
    private SessionService sessionService;
    // GlobalExceptionHandler (@ControllerAdvice) needs an AppLogger bean to load.
    @MockitoBean
    private AppLogger logger;

    @BeforeEach
    void setUp() {
        User caller = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe");
        Session session = new Session(ACCESS_TOKEN, caller, Instant.now().plusSeconds(3600));
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(java.util.Optional.of(session));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private OrganizationMember sampleMember(OrganizationMemberType type, Set<OrganizationMemberPermission> permissions) {
        Organization organization = new Organization("Acme");
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(type.name(), type, permissions));
        return member;
    }

    // ---- listOrganizations -------------------------------------------------

    @Test
    void listOrganizationsReturnsList() throws Exception {
        when(organizationService.listForCurrentUser())
                .thenReturn(List.of(new OrganizationDTO(
                        UUID.randomUUID(), "Acme", Instant.now(), true, 4L)));

        mockMvc.perform(get("/organizations")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Acme"))
                .andExpect(jsonPath("$.data[0].memberCount").value(4));
    }

    @Test
    void updateOrganizationReturnsTheUpdatedOrganization() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.updateOrganization(eq(orgId), any(UpdateOrganizationRequestDTO.class)))
                .thenReturn(new OrganizationDTO(orgId, "Acme Energy", Instant.now(), true, 3L));

        mockMvc.perform(patch("/organizations/" + orgId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationRequestDTO("Acme Energy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Organization updated"))
                .andExpect(jsonPath("$.data.name").value("Acme Energy"))
                .andExpect(jsonPath("$.data.memberCount").value(3));
    }

    @Test
    void updateOrganizationRejectsABlankNameWithValidationError() throws Exception {
        mockMvc.perform(patch("/organizations/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationRequestDTO("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(organizationService, never()).updateOrganization(any(), any());
    }

    @Test
    void updateOrganizationPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.updateOrganization(eq(orgId), any(UpdateOrganizationRequestDTO.class)))
                .thenThrow(new ForbiddenException("Only platform admins can update organizations"));

        mockMvc.perform(patch("/organizations/" + orgId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationRequestDTO("Acme Energy"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateOrganizationRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(patch("/organizations/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationRequestDTO("Acme Energy"))))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).updateOrganization(any(), any());
    }

    // ---- listMembers -------------------------------------------------------

    @Test
    void listMembersReturnsMembersWithIdentityFields() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        User user = new User("maria@enerscope.org", "Maria", "Paz", "hashed", PlatformRole.USER,
                "Senior Investment Analyst");
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                OrganizationMemberType.MEMBER.name(), OrganizationMemberType.MEMBER,
                Set.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        when(organizationService.listMembers(orgId)).thenReturn(List.of(member));

        mockMvc.perform(get("/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].firstName").value("Maria"))
                .andExpect(jsonPath("$.data[0].lastName").value("Paz"))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Senior Investment Analyst"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].memberType").value("MEMBER"));
    }

    @Test
    void listMembersRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/organizations/" + UUID.randomUUID() + "/members"))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).listMembers(any());
    }

    @Test
    void listMembersPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.listMembers(orgId))
                .thenThrow(new ForbiddenException("You are not allowed to view this organization"));

        mockMvc.perform(get("/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---- createOrganization ------------------------------------------------

    @Test
    void createOrganizationReturnsCreatedOrganization() throws Exception {
        when(organizationService.createOrganization(any(CreateOrganizationRequestDTO.class)))
                .thenReturn(new Organization("Acme"));

        mockMvc.perform(post("/organizations")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrganizationRequestDTO("Acme"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Organization created"))
                .andExpect(jsonPath("$.data.name").value("Acme"));
    }

    @Test
    void createOrganizationRejectsBlankNameWithValidationError() throws Exception {
        mockMvc.perform(post("/organizations")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(organizationService, never()).createOrganization(any());
    }

    @Test
    void createOrganizationPropagatesForbiddenWith403() throws Exception {
        when(organizationService.createOrganization(any(CreateOrganizationRequestDTO.class)))
                .thenThrow(new ForbiddenException("Only platform admins can create organizations"));

        mockMvc.perform(post("/organizations")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrganizationRequestDTO("Acme"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Only platform admins can create organizations"));
    }

    @Test
    void createOrganizationRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrganizationRequestDTO("Acme"))))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).createOrganization(any());
    }

    // ---- addMember ---------------------------------------------------------

    @Test
    void addMemberReturnsCreatedMember() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organizationService.addMember(eq(orgId), any(AddOrganizationMemberRequestDTO.class)))
                .thenReturn(sampleMember(OrganizationMemberType.OWNER,
                        Set.of(OrganizationMemberPermission.MANAGE_ORGANIZATION,
                                OrganizationMemberPermission.VIEW_ORGANIZATION)));

        mockMvc.perform(post("/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.OWNER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Member added"))
                .andExpect(jsonPath("$.data.memberType").value("OWNER"))
                .andExpect(jsonPath("$.data.userMail").value("jane@enerscope.org"));
    }

    @Test
    void addMemberRejectsUnknownOrganizationWith400() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organizationService.addMember(eq(orgId), any(AddOrganizationMemberRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Organization not found"));

        mockMvc.perform(post("/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddOrganizationMemberRequestDTO(userId, OrganizationMemberType.MEMBER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Organization not found"));
    }

    @Test
    void addMemberRejectsInvalidBodyWithValidationError() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(organizationService, never()).addMember(any(), any());
    }

    // ---- registerUser (organization owner / admin) -------------------------

    @Test
    void registerUserReturnsCreatedMember() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.registerUserInOrganization(eq(orgId), any(RegisterOrganizationUserRequestDTO.class)))
                .thenReturn(sampleMember(OrganizationMemberType.MEMBER,
                        Set.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));

        mockMvc.perform(post("/organizations/" + orgId + "/users")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterOrganizationUserRequestDTO(
                                "new@enerscope.org", "New", "User", "password123", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered into organization"))
                .andExpect(jsonPath("$.data.memberType").value("MEMBER"));
    }

    @Test
    void registerUserPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.registerUserInOrganization(eq(orgId), any(RegisterOrganizationUserRequestDTO.class)))
                .thenThrow(new ForbiddenException("You are not allowed to manage users in this organization"));

        mockMvc.perform(post("/organizations/" + orgId + "/users")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterOrganizationUserRequestDTO(
                                "new@enerscope.org", "New", "User", "password123", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registerUserRejectsInvalidBodyWithValidationError() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/organizations/" + orgId + "/users")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(organizationService, never()).registerUserInOrganization(any(), any());
    }

    // ---- bulk registration into an organization ----------------------------

    @Test
    void bulkRegisterUsersReturnsResultSummary() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(bulkRegistrationService.register(eq(orgId), anyString()))
                .thenReturn(new BulkRegistrationResultDTO(
                        2, 2, 0, "mail,password\r\njane@example.com,Secret-1\r\n", List.of()));

        MockMultipartFile file = new MockMultipartFile(
                "file", "users.csv", "text/csv",
                "mail,firstName,lastName\njane@example.com,Jane,Doe\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/organizations/" + orgId + "/users/bulk")
                        .file(file)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.credentialsCsv").exists());
    }

    @Test
    void bulkRegisterUsersPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(bulkRegistrationService.register(eq(orgId), anyString()))
                .thenThrow(new ForbiddenException("You are not allowed to manage users in this organization"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "users.csv", "text/csv",
                "mail,firstName,lastName\njane@example.com,Jane,Doe\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/organizations/" + orgId + "/users/bulk")
                        .file(file)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void removeMemberReturnsOk() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(delete("/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Member removed"));

        verify(organizationService).removeMember(orgId, memberId);
    }

    @Test
    void removeMemberAnswers404ForAnUnknownMember() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Member not found"))
                .when(organizationService).removeMember(orgId, memberId);

        mockMvc.perform(delete("/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Member not found"));
    }

    @Test
    void removeMemberPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doThrow(new ForbiddenException("You are not allowed to manage users in this organization"))
                .when(organizationService).removeMember(orgId, memberId);

        mockMvc.perform(delete("/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You are not allowed to manage users in this organization"));
    }

    @Test
    void removeMemberRequiresAuthenticationWith401() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(delete("/organizations/" + orgId + "/members/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).removeMember(any(), any());
    }

    @Test
    void addMemberPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.addMember(eq(orgId), any(AddOrganizationMemberRequestDTO.class)))
                .thenThrow(new ForbiddenException("You are not allowed to manage users in this organization"));

        mockMvc.perform(post("/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddOrganizationMemberRequestDTO(
                                UUID.randomUUID(), OrganizationMemberType.MEMBER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You are not allowed to manage users in this organization"));
    }

    @Test
    void addMemberRequiresAuthenticationWith401() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/organizations/" + orgId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddOrganizationMemberRequestDTO(
                                UUID.randomUUID(), OrganizationMemberType.MEMBER))))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).addMember(any(), any());
    }
    @Test
    void deleteOrganizationReturnsOk() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(delete("/organizations/" + orgId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Organization deleted"));

        verify(organizationService).deactivateOrganization(orgId);
    }

    @Test
    void deleteOrganizationPropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        doThrow(new ForbiddenException("Only platform admins can deactivate organizations"))
                .when(organizationService).deactivateOrganization(orgId);

        mockMvc.perform(delete("/organizations/" + orgId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOrganizationRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(delete("/organizations/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).deactivateOrganization(any());
    }

    @Test
    void reactivateOrganizationReturnsOk() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/organizations/" + orgId + "/reactivate")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Organization reactivated"));

        verify(organizationService).reactivateOrganization(orgId);
    }

    @Test
    void reactivateOrganizationRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/organizations/" + UUID.randomUUID() + "/reactivate"))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).reactivateOrganization(any());
    }

    @Test
    void listOwnedOrganizationsReturnsThem() throws Exception {
        when(organizationService.listOwnedByCurrentUser())
                .thenReturn(List.of(new OrganizationDTO(
                        UUID.randomUUID(), "Acme", Instant.now(), true, 3L)));

        mockMvc.perform(get("/organizations/owned")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Owned organizations"))
                .andExpect(jsonPath("$.data[0].name").value("Acme"));
    }

    @Test
    void listOwnedOrganizationsRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/organizations/owned"))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).listOwnedByCurrentUser();
    }

    @Test
    void changeMemberRoleReturnsTheUpdatedMember() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(organizationService.changeMemberRole(eq(orgId), eq(memberId),
                any(UpdateOrganizationMemberRoleRequestDTO.class)))
                .thenReturn(sampleMember(OrganizationMemberType.MEMBER,
                        Set.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));

        mockMvc.perform(patch("/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.MEMBER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Member role updated"))
                .andExpect(jsonPath("$.data.memberType").value("MEMBER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("VIEW_ORGANIZATION"));
    }

    @Test
    void changeMemberRoleRejectsAMissingRoleWith400() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(patch("/organizations/" + orgId + "/members/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(organizationService, never()).changeMemberRole(any(), any(), any());
    }

    @Test
    void changeMemberRoleRejectsAnUnknownRoleWith400() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(patch("/organizations/" + orgId + "/members/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberType\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());

        verify(organizationService, never()).changeMemberRole(any(), any(), any());
    }

    @Test
    void changeMemberRolePropagatesForbiddenWith403() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(organizationService.changeMemberRole(eq(orgId), eq(memberId),
                any(UpdateOrganizationMemberRoleRequestDTO.class)))
                .thenThrow(new ForbiddenException("You are not allowed to manage users in this organization"));

        mockMvc.perform(patch("/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You are not allowed to manage users in this organization"));
    }

    @Test
    void changeMemberRolePropagatesAnUnknownMemberWith404() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(organizationService.changeMemberRole(eq(orgId), eq(memberId),
                any(UpdateOrganizationMemberRoleRequestDTO.class)))
                .thenThrow(new EntityNotFoundException("Member not found"));

        mockMvc.perform(patch("/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Member not found"));
    }

    @Test
    void changeMemberRoleRequiresAuthenticationWith401() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(patch("/organizations/" + orgId + "/members/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateOrganizationMemberRoleRequestDTO(OrganizationMemberType.OWNER))))
                .andExpect(status().isUnauthorized());

        verify(organizationService, never()).changeMemberRole(any(), any(), any());
    }
}
