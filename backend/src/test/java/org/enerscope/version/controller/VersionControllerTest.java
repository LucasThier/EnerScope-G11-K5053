package org.enerscope.version.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.version.dto.CreateVersionRequestDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
 * Endpoint tests for {@link VersionController}. Uses {@code @WebMvcTest}
 * (web layer only, no database) with the real {@link SecurityConfig}/
 * {@link AuthFilter} so every request goes through the actual JWT filter
 * chain, like {@code ProjectControllerTest}. {@link VersionService} is
 * mocked.
 */
@WebMvcTest(VersionController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class VersionControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VersionService versionService;
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

    // ---- createVersion -------------------------------------------------------

    @Test
    void createVersionReturnsCreatedVersion() throws Exception {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        Version version = new Version("v1", project, null);
        when(versionService.createVersion(eq(projectId), any(CreateVersionRequestDTO.class)))
                .thenReturn(version);

        mockMvc.perform(post("/projects/" + projectId + "/versions")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateVersionRequestDTO("v1", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Version created"))
                .andExpect(jsonPath("$.data.name").value("v1"));
    }

    @Test
    void createVersionRejectsBlankNameWithValidationError() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/projects/" + projectId + "/versions")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation error"));

        verify(versionService, never()).createVersion(any(), any());
    }

    @Test
    void createVersionRejectsUnknownProjectWith400() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(versionService.createVersion(eq(projectId), any(CreateVersionRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Project not found"));

        mockMvc.perform(post("/projects/" + projectId + "/versions")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateVersionRequestDTO("v1", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Project not found"));
    }
}
