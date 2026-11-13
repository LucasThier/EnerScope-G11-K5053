package org.enerscope.comparison.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.comparison.dto.*;
import org.enerscope.comparison.service.ScenarioComparisonService;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScenarioComparisonController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class ScenarioComparisonControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ScenarioComparisonService service;

    @MockitoBean
    SessionService sessions;

    @MockitoBean
    AppLogger logger;

    final UUID project = UUID.randomUUID();
    final UUID version = UUID.randomUUID();
    final UUID evaluation = UUID.randomUUID();
    final UUID comparison = UUID.randomUUID();
    final String path = "/projects/" + project + "/comparisons";
    final String body = "{\"name\":\"Base vs B\",\"relevanceThreshold\":0.1,\"items\":[{\"versionId\":\"" + version
            + "\",\"evaluationId\":\"" + evaluation + "\"},{\"versionId\":\"" + version + "\"}]}";

    @BeforeEach
    void authenticate() {
        when(sessions.validate("test")).thenReturn(Optional.of(new Session("test",
                User.fromJwtClaims(UUID.randomUUID(), "test@example.com", "Test", "User"), Instant.now().plusSeconds(3600))));
    }

    ComparisonResultDTO emptyResult() {
        return new ComparisonResultDTO(evaluation, new BigDecimal("0.1"), new BigDecimal("0.01"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void previewBindsTheRequestAndAnswersInTheEnvelope() throws Exception {
        when(service.preview(eq(project), any())).thenReturn(emptyResult());

        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.baselineEvaluationId").value(evaluation.toString()));
        verify(service).preview(project, new ComparisonRequestDTO("Base vs B", null, new BigDecimal("0.1"), null,
                List.of(new ComparisonRequestDTO.Item(version, evaluation), new ComparisonRequestDTO.Item(version, null))));
    }

    @Test
    void saveAnswers201AndReadRoutesKeepTheScopedIdentifiers() throws Exception {
        var saved = new SavedComparisonDTO(comparison, "Base vs B", null, new BigDecimal("0.1"), new BigDecimal("0.01"),
                Instant.now(), List.of(), emptyResult());
        when(service.save(eq(project), any())).thenReturn(saved);
        when(service.get(project, comparison)).thenReturn(saved);
        when(service.list(project)).thenReturn(List.of(new ComparisonSummaryDTO(comparison, "Base vs B", null, 2, Instant.now())));

        mvc.perform(post(path).header("Authorization", "Bearer test").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(comparison.toString()));
        mvc.perform(get(path).header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scenarioCount").value(2));
        mvc.perform(get(path + "/" + comparison).header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Base vs B"));
        mvc.perform(delete(path + "/" + comparison).header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).delete(project, comparison);
    }

    @Test
    void anonymousRequestsCannotCompareReadOrDelete() throws Exception {
        for (var request : List.of(post(path + "/preview").contentType("application/json").content(body),
                post(path).contentType("application/json").content(body), get(path), get(path + "/" + comparison),
                delete(path + "/" + comparison))) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(service);
    }

    @Test
    void serviceErrorsMapToTheGlobalStatusCodes() throws Exception {
        when(service.preview(any(), any())).thenThrow(new IllegalArgumentException("Pick at least 2 evaluations to compare"));
        when(service.save(any(), any())).thenThrow(new ForbiddenException("Project permission required"));
        when(service.get(any(), any())).thenThrow(new EntityNotFoundException("Comparison not found"));

        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post(path).header("Authorization", "Bearer test").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get(path + "/" + comparison).header("Authorization", "Bearer test"))
                .andExpect(status().isNotFound());
    }
}
