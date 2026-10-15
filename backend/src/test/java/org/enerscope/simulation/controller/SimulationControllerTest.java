package org.enerscope.simulation.controller;

import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.simulation.dto.CostEstimateDTO;
import org.enerscope.simulation.dto.RunSimulationRequestDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.dto.SimulationKpisDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.service.SimulationService;
import org.enerscope.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoint tests for {@link SimulationController}, behind the real security chain. */
@WebMvcTest(SimulationController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class SimulationControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";
    private static final String AUTH = "Bearer " + ACCESS_TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private AppLogger logger;

    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User caller = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe");
        Session session = new Session(ACCESS_TOKEN, caller, Instant.now().plusSeconds(3600));
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(Optional.of(session));
    }

    private SimulationSummaryDTO summary() {
        SimulationKpisDTO kpis = new SimulationKpisDTO(480, 350, 500, 700, 300, 200, 40,
                new BigDecimal("1000.00"), new BigDecimal("40.00"), new BigDecimal("20.00"),
                new BigDecimal("1060.00"), new BigDecimal("2.1200"));
        return new SimulationSummaryDTO(UUID.randomUUID(), versionId, Instant.now(), 20, 10, 4, kpis, List.of());
    }

    @Test
    void runSimulationAnswers201WithTheRun() throws Exception {
        when(simulationService.runSimulation(eq(versionId), any()))
                .thenReturn(new SimulationDetailDTO(summary(), List.of(), List.of()));

        mockMvc.perform(post("/version/" + versionId + "/simulations")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"years\":20,\"rounds\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary.years").value(20))
                .andExpect(jsonPath("$.data.summary.kpis.deliveredP50").value(500.0))
                .andExpect(jsonPath("$.data.summary.kpis.unitCost").value(2.12));
        verify(simulationService).runSimulation(versionId, new RunSimulationRequestDTO(20, 10));
    }

    @Test
    void runSimulationRejectsAMissingHorizonWith400() throws Exception {
        mockMvc.perform(post("/version/" + versionId + "/simulations")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rounds\":10}"))
                .andExpect(status().isBadRequest());
        verify(simulationService, never()).runSimulation(any(), any());
    }

    @Test
    void runSimulationRejectsAHorizonAboveTheCapWith400() throws Exception {
        mockMvc.perform(post("/version/" + versionId + "/simulations")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"years\":" + (RunSimulationRequestDTO.MAX_YEARS + 1) + "}"))
                .andExpect(status().isBadRequest());
        verify(simulationService, never()).runSimulation(any(), any());
    }

    @Test
    void runSimulationPropagatesForbiddenWith403() throws Exception {
        when(simulationService.runSimulation(eq(versionId), any()))
                .thenThrow(new ForbiddenException("You are not allowed to edit this project"));

        mockMvc.perform(post("/version/" + versionId + "/simulations")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"years\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to edit this project"));
    }

    @Test
    void runSimulationRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/version/" + versionId + "/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"years\":5}"))
                .andExpect(status().isUnauthorized());
        verify(simulationService, never()).runSimulation(any(), any());
    }

    @Test
    void listSimulationsReturnsTheRuns() throws Exception {
        when(simulationService.listSimulations(versionId)).thenReturn(List.of(summary(), summary()));

        mockMvc.perform(get("/version/" + versionId + "/simulations").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].kpis.totalCost").value(1060.0));
    }

    @Test
    void getSimulationAnswers404ForAnUnknownRun() throws Exception {
        UUID runId = UUID.randomUUID();
        when(simulationService.getSimulation(versionId, runId))
                .thenThrow(new EntityNotFoundException("Simulation not found with id: " + runId));

        mockMvc.perform(get("/version/" + versionId + "/simulations/" + runId).header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSimulationDelegatesToTheService() throws Exception {
        UUID runId = UUID.randomUUID();

        mockMvc.perform(delete("/version/" + versionId + "/simulations/" + runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Simulation deleted"));
        verify(simulationService).deleteSimulation(versionId, runId);
    }

    @Test
    void deleteSimulationPropagatesForbiddenWith403() throws Exception {
        UUID runId = UUID.randomUUID();
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(simulationService).deleteSimulation(versionId, runId);

        mockMvc.perform(delete("/version/" + versionId + "/simulations/" + runId).header("Authorization", AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    void estimateCostsDefaultsToTwentyYears() throws Exception {
        when(simulationService.estimateCosts(versionId, 20)).thenReturn(new CostEstimateDTO(20,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("3"), List.of()));

        mockMvc.perform(get("/version/" + versionId + "/costs").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.years").value(20))
                .andExpect(jsonPath("$.data.totalCost").value(3));
    }

    @Test
    void estimateCostsTakesTheHorizonFromTheQuery() throws Exception {
        when(simulationService.estimateCosts(versionId, 5)).thenReturn(new CostEstimateDTO(5,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        mockMvc.perform(get("/version/" + versionId + "/costs?years=5").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.years").value(5));
        verify(simulationService).estimateCosts(versionId, 5);
    }
}
