package org.enerscope.simulation.controller;

import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.simulation.dto.VersionComparisonDTO;
import org.enerscope.simulation.service.VersionComparisonService;
import org.enerscope.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoint tests for {@link ComparisonController}, behind the real security chain. */
@WebMvcTest(ComparisonController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class ComparisonControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";
    private static final String AUTH = "Bearer " + ACCESS_TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VersionComparisonService comparisonService;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private AppLogger logger;

    private final UUID projectId = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User caller = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe");
        Session session = new Session(ACCESS_TOKEN, caller, Instant.now().plusSeconds(3600));
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(Optional.of(session));
    }

    @Test
    void compareTakesTheVersionsInTheOrderGiven() throws Exception {
        when(comparisonService.compare(projectId, List.of(first, second)))
                .thenReturn(new VersionComparisonDTO(projectId, first, List.of(), List.of()));

        mockMvc.perform(get("/projects/" + projectId + "/comparison")
                        .param("versionIds", first + "," + second)
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baselineVersionId").value(first.toString()));
        verify(comparisonService).compare(projectId, List.of(first, second));
    }

    @Test
    void compareWithoutVersionsLetsTheServiceAnswer400() throws Exception {
        when(comparisonService.compare(eq(projectId), any()))
                .thenThrow(new IllegalArgumentException("Pick at least two different versions to compare"));

        mockMvc.perform(get("/projects/" + projectId + "/comparison").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick at least two different versions to compare"));
    }

    @Test
    void compareRejectsAMalformedVersionIdWith400() throws Exception {
        mockMvc.perform(get("/projects/" + projectId + "/comparison")
                        .param("versionIds", "not-a-uuid," + second)
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
        verify(comparisonService, never()).compare(any(), any());
    }

    @Test
    void comparePropagatesForbiddenWith403() throws Exception {
        when(comparisonService.compare(eq(projectId), any()))
                .thenThrow(new ForbiddenException("You are not allowed to view this project"));

        mockMvc.perform(get("/projects/" + projectId + "/comparison")
                        .param("versionIds", first + "," + second)
                        .header("Authorization", AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    void compareRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/projects/" + projectId + "/comparison").param("versionIds", first + "," + second))
                .andExpect(status().isUnauthorized());
        verify(comparisonService, never()).compare(any(), any());
    }
}
