package org.enerscope.organization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.common.ForbiddenException;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.BulkRegistrationResultDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .thenReturn(List.of(new Organization("Acme")));

        mockMvc.perform(get("/organizations")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Acme"));
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
                                "new@enerscope.org", "New", "User", "password123"))))
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
                                "new@enerscope.org", "New", "User", "password123"))))
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
}
