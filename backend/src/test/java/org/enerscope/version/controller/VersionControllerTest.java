package org.enerscope.version.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.version.dto.VersionDTO;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link VersionController}. Uses {@code @WebMvcTest} with
 * the real {@link SecurityConfig}/{@link AuthFilter}, like the project and
 * organization controller tests, so an unauthenticated request is rejected by
 * the actual filter chain rather than by a stub.
 *
 * <p>These endpoints take nothing but a version UUID, which is what made them
 * worth guarding: the cases below cover the happy path, the 403 raised when the
 * caller has no claim on the owning project, and the 401 when there is no token
 * at all.</p>
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
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(Optional.of(session));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Version sampleVersion() {
        return new Version("Baseline", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
    }

    private VersionDTO versionDTO() {
        VersionDTO data = new VersionDTO();
        data.setName("Baseline");
        return data;
    }

    // ---- getVersion ----------------------------------------------------------

    @Test
    void getVersionReturnsTheVersion() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(versionService.getVersion(versionId)).thenReturn(sampleVersion());

        mockMvc.perform(get("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Version retrieved successfully"))
                .andExpect(jsonPath("$.data.name").value("Baseline"));
    }

    @Test
    void getVersionPropagatesForbiddenWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(versionService.getVersion(versionId))
                .thenThrow(new ForbiddenException("You are not allowed to view this project"));

        mockMvc.perform(get("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You are not allowed to view this project"));
    }

    @Test
    void getVersionRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/version/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(versionService, never()).getVersion(any());
    }

    // ---- deleteVersion -------------------------------------------------------

    @Test
    void deleteVersionPropagatesForbiddenWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(versionService).deleteVersion(versionId);

        mockMvc.perform(delete("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to edit this project"));
    }

    @Test
    void deleteVersionRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(delete("/version/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(versionService, never()).deleteVersion(any());
    }

    // ---- addNodeToVersion ----------------------------------------------------

    @Test
    void addNodeToVersionPropagatesForbiddenWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");
        when(versionService.addNodeToVersion(eq(versionId), any()))
                .thenThrow(new ForbiddenException("You are not allowed to edit this project"));

        mockMvc.perform(post("/version/" + versionId + "/node")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(wellDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addNodeToVersionRequiresAuthenticationWith401() throws Exception {
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");

        mockMvc.perform(post("/version/" + UUID.randomUUID() + "/node")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(wellDTO)))
                .andExpect(status().isUnauthorized());

        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    // ---- createVersion (detached) --------------------------------------------

    @Test
    void createDetachedVersionReturnsTheVersionForAPlatformAdmin() throws Exception {
        when(versionService.saveOrphanVersion(any(VersionDTO.class))).thenReturn(sampleVersion());

        mockMvc.perform(post("/version/createtest")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(versionDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Baseline"));
    }

    @Test
    void createDetachedVersionPropagatesForbiddenWith403() throws Exception {
        when(versionService.saveOrphanVersion(any(VersionDTO.class)))
                .thenThrow(new ForbiddenException("Only platform admins can create detached versions"));

        mockMvc.perform(post("/version/createtest")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(versionDTO())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only platform admins can create detached versions"));
    }

    @Test
    void createDetachedVersionRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/version/createtest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(versionDTO())))
                .andExpect(status().isUnauthorized());

        verify(versionService, never()).saveOrphanVersion(any());
    }
}
