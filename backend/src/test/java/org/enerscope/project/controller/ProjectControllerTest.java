package org.enerscope.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.dto.CreateProjectRequestDTO;
import org.enerscope.project.dto.ProjectSummaryDTO;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.project.service.ProjectService;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link ProjectController}. Uses {@code @WebMvcTest}
 * (web layer only, no database) with the real {@link SecurityConfig}/
 * {@link AuthFilter} so every request goes through the actual JWT filter
 * chain, like {@code OrganizationControllerTest}. {@link ProjectService} is
 * mocked.
 */
@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class ProjectControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectService projectService;
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

    private ProjectMember sampleMember(ProjectMemberType type, Set<ProjectMemberPermission> permissions) {
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        User user = new User("jane@enerscope.org", "Jane", "Doe", "hashed");
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(type.name(), type, permissions));
        return member;
    }

    // ---- listProjects --------------------------------------------------------

    @Test
    void listProjectsReturnsSummaries() throws Exception {
        when(projectService.listForCurrentUser(null)).thenReturn(List.of(new ProjectSummaryDTO(
                UUID.randomUUID(), "Grid Expansion", "Expands the regional grid",
                UUID.randomUUID(), "Acme", 4L, Instant.now())));

        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Grid Expansion"))
                .andExpect(jsonPath("$.data[0].organizationName").value("Acme"))
                .andExpect(jsonPath("$.data[0].memberCount").value(4));
    }

    @Test
    void listProjectsForwardsOrganizationFilter() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(projectService.listForCurrentUser(orgId)).thenReturn(List.of());

        mockMvc.perform(get("/projects")
                        .param("organizationId", orgId.toString())
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(projectService).listForCurrentUser(orgId);
    }

    @Test
    void listProjectsRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());

        verify(projectService, never()).listForCurrentUser(any());
    }

    // ---- listMembers -------------------------------------------------------

    @Test
    void listMembersReturnsMembers() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectService.listMembers(projectId)).thenReturn(List.of(
                sampleMember(ProjectMemberType.EDITOR,
                        Set.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT))));

        mockMvc.perform(get("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project members"))
                .andExpect(jsonPath("$.data[0].userMail").value("jane@enerscope.org"))
                .andExpect(jsonPath("$.data[0].firstName").value("Jane"))
                .andExpect(jsonPath("$.data[0].lastName").value("Doe"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].memberType").value("EDITOR"));
    }

    @Test
    void listMembersRequiresAuthenticationWith401() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/projects/" + projectId + "/members"))
                .andExpect(status().isUnauthorized());

        verify(projectService, never()).listMembers(any());
    }

    // ---- createVersion -------------------------------------------------------

    @Test
    void createVersionReturnsCreatedVersion() throws Exception {
        UUID projectId = UUID.randomUUID();
        Version version = new Version();
        version.setName("Baseline");
        when(projectService.saveVersion(eq(projectId), any(VersionDTO.class))).thenReturn(version);

        mockMvc.perform(post("/projects/" + projectId + "/version")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VersionDTO("Baseline", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Version created successfully"))
                .andExpect(jsonPath("$.data.name").value("Baseline"));
    }

    @Test
    void createVersionRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/projects/" + UUID.randomUUID() + "/version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VersionDTO("Baseline", null))))
                .andExpect(status().isUnauthorized());

        verify(projectService, never()).saveVersion(any(), any());
    }

    // ---- createProject -------------------------------------------------------

    @Test
    void createProjectReturnsCreatedProject() throws Exception {
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization("Acme");
        when(projectService.createProject(any(CreateProjectRequestDTO.class)))
                .thenReturn(new Project("Grid Expansion", "Expands the regional grid", organization));

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateProjectRequestDTO(
                                "Grid Expansion", "Expands the regional grid", orgId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project created"))
                .andExpect(jsonPath("$.data.name").value("Grid Expansion"))
                .andExpect(jsonPath("$.data.description").value("Expands the regional grid"));
    }

    @Test
    void createProjectRejectsBlankFieldsWithValidationError() throws Exception {
        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(projectService, never()).createProject(any());
    }

    // ---- addMember -------------------------------------------------------

    @Test
    void addMemberReturnsCreatedMember() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projectService.addMember(eq(projectId), any(AddProjectMemberRequestDTO.class)))
                .thenReturn(sampleMember(ProjectMemberType.ADMIN,
                        Set.of(ProjectMemberPermission.MANAGE_PROJECT, ProjectMemberPermission.EDIT_PROJECT,
                                ProjectMemberPermission.VIEW_PROJECT)));

        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddProjectMemberRequestDTO(userId, ProjectMemberType.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Member added"))
                .andExpect(jsonPath("$.data.memberType").value("ADMIN"))
                .andExpect(jsonPath("$.data.userMail").value("jane@enerscope.org"));
    }

    @Test
    void addMemberRejectsUnknownProjectWith400() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projectService.addMember(eq(projectId), any(AddProjectMemberRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Project not found"));

        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddProjectMemberRequestDTO(userId, ProjectMemberType.EDITOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    @Test
    void addMemberRejectsInvalidBodyWithValidationError() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(projectService, never()).addMember(any(), any());
    }
}
