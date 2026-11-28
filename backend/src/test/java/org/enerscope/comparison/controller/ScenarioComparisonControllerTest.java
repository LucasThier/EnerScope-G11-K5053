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
import org.enerscope.comparison.model.enums.ScenarioStatus;
import org.enerscope.comparison.service.ScenarioComparisonService;
import org.enerscope.comparison.service.ScenarioStatusService;
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
    ScenarioStatusService statuses;

    @MockitoBean
    SessionService sessions;

    @MockitoBean
    AppLogger logger;

    final UUID project = UUID.randomUUID();
    final UUID version = UUID.randomUUID();
    final UUID evaluation = UUID.randomUUID();
    final UUID comparison = UUID.randomUUID();
    final String path = "/projects/" + project + "/comparisons";
    final UUID otherVersion = UUID.randomUUID();
    final String body = "{\"name\":\"Base vs B\",\"relevanceThreshold\":0.1,\"items\":[{\"scenarioId\":\"" + version
            + "\"},{\"scenarioId\":\"" + otherVersion + "\"}]}";

    @BeforeEach
    void authenticate() {
        when(sessions.validate("test")).thenReturn(Optional.of(new Session("test",
                User.fromJwtClaims(UUID.randomUUID(), "test@example.com", "Test", "User"), Instant.now().plusSeconds(3600))));
    }

    ComparisonResultDTO emptyResult() {
        return new ComparisonResultDTO(new BigDecimal("0.1"), new BigDecimal("0.01"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void previewBindsTheRequestAndAnswersInTheEnvelope() throws Exception {
        when(service.preview(eq(project), any())).thenReturn(emptyResult());

        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rateThreshold").value(0.01));
        verify(service).preview(project, new ComparisonRequestDTO("Base vs B", null, new BigDecimal("0.1"), null,
                List.of(new ComparisonRequestDTO.Item(version), new ComparisonRequestDTO.Item(otherVersion))));
    }

    @Test
    void aRequestThatNamesOnlyAVersionStillBindsForCompatibility() throws Exception {
        when(service.preview(eq(project), any())).thenReturn(emptyResult());
        String legacy = "{\"items\":[{\"versionId\":\"" + version + "\"},{\"versionId\":\"" + otherVersion + "\"}]}";

        mvc.perform(post(path + "/preview").header("Authorization", "Bearer test")
                        .contentType("application/json").content(legacy))
                .andExpect(status().isOk());
        verify(service).preview(project, new ComparisonRequestDTO(null, null, null, null,
                List.of(ComparisonRequestDTO.Item.ofVersion(version), ComparisonRequestDTO.Item.ofVersion(otherVersion))));
    }

    @Test
    void saveAnswers201AndReadRoutesKeepTheScopedIdentifiers() throws Exception {
        var saved = new SavedComparisonDTO(comparison, "Base vs B", null, new BigDecimal("0.1"), new BigDecimal("0.01"),
                Instant.now(), List.of(), emptyResult());
        when(service.save(eq(project), any())).thenReturn(saved);
        when(service.get(project, comparison)).thenReturn(saved);
        when(service.list(project)).thenReturn(List.of(new ComparisonSummaryDTO(comparison, "Base vs B", null, List.of("Base", "B"), false, Instant.now())));

        mvc.perform(post(path).header("Authorization", "Bearer test").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(comparison.toString()));
        mvc.perform(get(path).header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scenarios[1]").value("B"));
        mvc.perform(get(path + "/" + comparison).header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Base vs B"));
        mvc.perform(delete(path + "/" + comparison).header("Authorization", "Bearer test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).delete(project, comparison);
    }

    @Test
    void scenarioStatusesAreListedForThePicker() throws Exception {
        when(statuses.list(project)).thenReturn(List.of(new ComparableScenarioDTO(project, "Proyecto", version, "Base",
                UUID.randomUUID(), "Escenario base", true, ScenarioStatus.OUTDATED, evaluation, Instant.now(), null, null, true, List.of(), true, null)));

        mvc.perform(get(path + "/scenarios").header("Authorization", "Bearer test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("OUTDATED"))
                .andExpect(jsonPath("$.data[0].canSimulate").value(true));
        verify(statuses).list(project);
        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestsCannotCompareReadOrDelete() throws Exception {
        for (var request : List.of(get(path + "/scenarios"), post(path + "/preview").contentType("application/json").content(body),
                post(path).contentType("application/json").content(body), get(path), get(path + "/" + comparison),
                delete(path + "/" + comparison))) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(service, statuses);
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
