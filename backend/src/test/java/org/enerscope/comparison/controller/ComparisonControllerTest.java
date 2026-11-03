package org.enerscope.comparison.controller;

import java.time.Instant;
import java.util.*;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.comparison.service.ComparisonService;
import org.enerscope.common.ForbiddenException;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComparisonController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class ComparisonControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    ComparisonService service;
    @MockitoBean
    SessionService sessions;
    @MockitoBean
    AppLogger logger;
    final String path = "/projects/" + UUID.randomUUID() + "/comparisons";

    @BeforeEach
    void authentication() {
        when(sessions.validate("test-token")).thenReturn(Optional.of(new Session("test-token", User.fromJwtClaims(UUID.randomUUID(), "test@example.com", "Test", "User"), Instant.now().plusSeconds(3600))));
    }

    @Test
    void anonymousRequestsCannotReadOrCalculateOrSave() throws Exception {
        for (var request : List.of(get(path), get(path + "/" + UUID.randomUUID()), post(path), post(path + "/preview"))) mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedEndpointsUseEnvelopeAndCreatedForSave() throws Exception {
        when(service.list(any())).thenReturn(List.of());
        mvc.perform(get(path).header("Authorization", "Bearer test-token")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").isArray());
        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test-token").contentType("application/json").content("{}")).andExpect(status().isOk());
        mvc.perform(post(path).header("Authorization", "Bearer test-token").contentType("application/json").content("{}")).andExpect(status().isCreated());
        mvc.perform(get(path + "/" + UUID.randomUUID()).header("Authorization", "Bearer test-token")).andExpect(status().isOk());
    }

    @Test
    void permissionDenialReturnsForbidden() throws Exception {
        when(service.preview(any(), any())).thenThrow(new ForbiddenException("Denied"));
        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test-token").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void invalidConfigurationReturnsBadRequest() throws Exception {
        when(service.preview(any(), any())).thenThrow(new IllegalArgumentException("Weights must total 100"));
        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test-token").contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }
}
