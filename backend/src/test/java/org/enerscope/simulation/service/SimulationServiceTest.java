package org.enerscope.simulation.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.simulation.dto.CostEstimateDTO;
import org.enerscope.simulation.dto.RunSimulationRequestDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.repository.FinalResultRepository;
import org.enerscope.simulator.Simulator;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.enerscope.simulation.SimulationFixtures.domesticChain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimulationServiceTest {

    private VersionRepository versionRepository;
    private FinalResultRepository finalResultRepository;
    private ProjectAccessGuard accessGuard;
    private SimulationService service;

    @BeforeEach
    void setUp() {
        versionRepository = mock(VersionRepository.class);
        finalResultRepository = mock(FinalResultRepository.class);
        accessGuard = mock(ProjectAccessGuard.class);
        service = new SimulationService(versionRepository, finalResultRepository, accessGuard, new CostCalculator(),
                new SimulationResultMapper(), mock(AppLogger.class));
    }

    private Version stored(Version version) {
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        return version;
    }

    private static FinalResult run(Instant createdAt) {
        FinalResult result = new FinalResult(10);
        ReflectionTestUtils.setField(result, "id", UUID.randomUUID());
        result.setCreatedAt(createdAt);
        return result;
    }

    @Test
    void runSimulationStoresTheRunWithTheCostOfTheModel() {
        Version version = stored(domesticChain("Base"));

        SimulationDetailDTO detail = service.runSimulation(version.getId(), new RunSimulationRequestDTO(1, 2));

        assertEquals(1, version.getFinalResults().size());
        FinalResult stored = version.getFinalResults().get(0);
        assertEquals(1, stored.getTime());
        assertEquals(2, stored.getRounds());
        assertEquals(3, stored.getNodeCosts().size());
        // Three nodes with a FLAT 1000 investment each, and 100/month for 365 days.
        assertEquals(new BigDecimal("3000.00"), stored.getCapex());
        assertEquals(new BigDecimal("3650.01"), stored.getOpex());
        verify(finalResultRepository).saveAndFlush(stored);

        SimulationSummaryDTO summary = detail.summary();
        assertEquals(version.getId(), summary.versionId());
        // 100/h from the well, 10 % lost in the network, 8761 simulated hours.
        assertEquals(90f * 8761, summary.kpis().deliveredP50(), 1.0);
        assertEquals(10f * 8761, summary.kpis().lossesP50(), 1.0);
        assertEquals(3, detail.nodes().size());
        assertTrue(summary.warnings().isEmpty());
    }

    @Test
    void runSimulationDefaultsToTheEngineRoundCount() {
        Version version = stored(domesticChain("Base"));

        service.runSimulation(version.getId(), new RunSimulationRequestDTO(1, null));

        assertEquals(Simulator.DEFAULT_ROUNDS, version.getFinalResults().get(0).getRounds());
    }

    @Test
    void runSimulationChecksTheEditPermissionAfterResolvingTheVersion() {
        Version version = stored(domesticChain("Base"));
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanEditVersion(version.getId());

        assertThrows(ForbiddenException.class,
                () -> service.runSimulation(version.getId(), new RunSimulationRequestDTO(1, 1)));
        assertTrue(version.getFinalResults().isEmpty());
        verify(finalResultRepository, never()).saveAndFlush(any());
    }

    @Test
    void runSimulationAnswersNotFoundForAnUnknownVersionBeforeAuthorizing() {
        UUID unknown = UUID.randomUUID();
        when(versionRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class,
                () -> service.runSimulation(unknown, new RunSimulationRequestDTO(1, 1)));
        verifyNoInteractions(accessGuard);
    }

    @Test
    void runSimulationRejectsAHorizonOrRoundCountOutOfRange() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.runSimulation(id, new RunSimulationRequestDTO(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.runSimulation(id, new RunSimulationRequestDTO(RunSimulationRequestDTO.MAX_YEARS + 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.runSimulation(id, new RunSimulationRequestDTO(1, RunSimulationRequestDTO.MAX_ROUNDS + 1)));
        assertThrows(IllegalArgumentException.class, () -> service.runSimulation(id, new RunSimulationRequestDTO(null, 1)));
        verifyNoInteractions(versionRepository);
    }

    @Test
    void runSimulationRejectsAVersionWithNothingLeftToSimulate() {
        Version version = stored(domesticChain("Base"));
        version.getNodeSnapshot().forEach(node -> node.setState(NodeStateEnum.REMOVED));

        assertThrows(IllegalArgumentException.class,
                () -> service.runSimulation(version.getId(), new RunSimulationRequestDTO(1, 1)));
    }

    @Test
    void runSimulationLeavesRemovedNodesOutOfTheCosts() {
        Version version = stored(domesticChain("Base"));
        BaseNode consumption = version.getNodeSnapshot().get(2);
        consumption.setState(NodeStateEnum.REMOVED);

        SimulationDetailDTO detail = service.runSimulation(version.getId(), new RunSimulationRequestDTO(1, 1));

        assertEquals(2, detail.nodes().size());
        assertEquals(new BigDecimal("2000.00"), detail.summary().kpis().capex());
        assertTrue(detail.summary().warnings().contains(SimulationResultMapper.WARNING_NO_SINKS));
    }

    @Test
    void listSimulationsReturnsTheNewestRunFirst() {
        Version version = stored(domesticChain("Base"));
        FinalResult older = run(Instant.parse("2026-10-01T10:00:00Z"));
        FinalResult newer = run(Instant.parse("2026-10-02T10:00:00Z"));
        version.getFinalResults().addAll(List.of(older, newer));

        List<SimulationSummaryDTO> runs = service.listSimulations(version.getId());

        assertEquals(List.of(newer.getId(), older.getId()), runs.stream().map(SimulationSummaryDTO::id).toList());
        verify(accessGuard).assertCanViewVersion(version.getId());
    }

    @Test
    void listSimulationsRequiresAccessToTheVersion() {
        Version version = stored(domesticChain("Base"));
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanViewVersion(version.getId());

        assertThrows(ForbiddenException.class, () -> service.listSimulations(version.getId()));
    }

    @Test
    void getSimulationRefusesARunOfAnotherVersion() {
        Version version = stored(domesticChain("Base"));
        version.getFinalResults().add(run(Instant.now()));

        assertThrows(EntityNotFoundException.class,
                () -> service.getSimulation(version.getId(), UUID.randomUUID()));
    }

    @Test
    void deleteSimulationRemovesTheRunFromTheVersion() {
        Version version = stored(domesticChain("Base"));
        FinalResult kept = run(Instant.now());
        FinalResult deleted = run(Instant.now());
        version.getFinalResults().addAll(List.of(kept, deleted));

        service.deleteSimulation(version.getId(), deleted.getId());

        assertEquals(List.of(kept), version.getFinalResults());
        verify(accessGuard).assertCanEditVersion(version.getId());
        verify(versionRepository).save(version);
    }

    @Test
    void deleteSimulationRequiresTheEditPermission() {
        Version version = stored(domesticChain("Base"));
        FinalResult existing = run(Instant.now());
        version.getFinalResults().add(existing);
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanEditVersion(version.getId());

        assertThrows(ForbiddenException.class, () -> service.deleteSimulation(version.getId(), existing.getId()));
        assertEquals(1, version.getFinalResults().size());
    }

    @Test
    void estimateCostsPricesTheCurrentModelWithoutSimulating() {
        Version version = stored(domesticChain("Base"));

        CostEstimateDTO estimate = service.estimateCosts(version.getId(), 1);

        assertEquals(new BigDecimal("3000.00"), estimate.capex());
        assertEquals(new BigDecimal("3650.01"), estimate.opex());
        assertEquals(new BigDecimal("6650.01"), estimate.totalCost());
        assertEquals(List.of("EXTRACTION", "INTERNAL_CONSUMPTION"),
                estimate.verticals().stream().map(v -> v.vertical()).toList());
        assertNull(estimate.verticals().get(0).lossesP50(), "nothing was simulated");
        assertTrue(version.getFinalResults().isEmpty());
        verify(accessGuard).assertCanViewVersion(version.getId());
    }

    @Test
    void estimateCostsRejectsAHorizonOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> service.estimateCosts(UUID.randomUUID(), 0));
        verifyNoInteractions(versionRepository);
    }
}
