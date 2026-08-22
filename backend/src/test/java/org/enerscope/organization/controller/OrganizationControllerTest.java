package org.enerscope.organization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.dto.CreateProjectRequestDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.Project;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    // ---- addProject --------------------------------------------------------

    @Test
    void addProjectReturnsCreatedProject() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        when(organizationService.addProject(eq(orgId), any(CreateProjectRequestDTO.class)))
                .thenReturn(new Project("Grid Expansion", "Expands the regional grid", organization));

        mockMvc.perform(post("/organizations/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateProjectRequestDTO("Grid Expansion", "Expands the regional grid"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project added"))
                .andExpect(jsonPath("$.data.name").value("Grid Expansion"))
                .andExpect(jsonPath("$.data.description").value("Expands the regional grid"));
    }

    @Test
    void addProjectRejectsBlankFieldsWithValidationError() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/organizations/" + orgId + "/projects")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(organizationService, never()).addProject(any(), any());
    }
}
