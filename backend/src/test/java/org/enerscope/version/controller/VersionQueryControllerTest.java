package org.enerscope.version.controller;

import java.time.Instant;
import java.util.*;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.common.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.version.dto.VersionSummaryDTO;
import org.enerscope.version.service.VersionQueryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VersionQueryController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class VersionQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VersionQueryService service;
    @MockitoBean SessionService sessions;
    @MockitoBean AppLogger logger;
    final UUID projectId = UUID.randomUUID();
    String path() { return "/projects/" + projectId + "/versions"; }
    @BeforeEach void setup() {
        var user = User.fromJwtClaims(UUID.randomUUID(), "reader@example.com", "Reader", "User");
        when(sessions.validate("test")).thenReturn(Optional.of(new Session("test", user, Instant.now().plusSeconds(3600))));
    }
    @Test void anonymousRequestReturns401() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void authorizedRequestReturnsSummaryEnvelope() throws Exception {
        when(service.list(projectId)).thenReturn(List.of(new VersionSummaryDTO(UUID.randomUUID(), "Base", Instant.now(),
                List.of(new VersionSummaryDTO.NodeSummary(UUID.randomUUID(), "Well", "WELL")))));
        mvc.perform(get(path()).header("Authorization", "Bearer test")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data[0].name").value("Base"))
                .andExpect(jsonPath("$.data[0].nodes[0].type").value("WELL"))
                .andExpect(jsonPath("$.data[0].nodeSnapshot").doesNotExist());
    }
    @Test void forbiddenRequestReturns403() throws Exception {
        when(service.list(projectId)).thenThrow(new ForbiddenException("Forbidden"));
        mvc.perform(get(path()).header("Authorization", "Bearer test")).andExpect(status().isForbidden());
    }
    @Test void missingProjectReturns404() throws Exception {
        when(service.list(projectId)).thenThrow(new EntityNotFoundException("Project not found"));
        mvc.perform(get(path()).header("Authorization", "Bearer test")).andExpect(status().isNotFound());
    }
}
