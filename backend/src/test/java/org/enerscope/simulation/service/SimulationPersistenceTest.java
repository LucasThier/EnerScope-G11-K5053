package org.enerscope.simulation.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.simulation.SimulationFixtures;
import org.enerscope.simulation.dto.RunSimulationRequestDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.repository.FinalResultRepository;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A simulation run against the real schema: the engine's output, the per-node
 * costs and the three percentile join tables have to survive a round trip
 * through the database, and deleting a run has to take all of it along. Only a
 * database can answer that — the mapping test the 2026-10-05 entry asked for,
 * on the H2 schema Hibernate builds from the entities.
 */
@DataJpaTest
@ActiveProfiles("test")
class SimulationPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private VersionRepository versionRepository;
    @Autowired
    private FinalResultRepository finalResultRepository;

    private SimulationService service;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        service = new SimulationService(versionRepository, finalResultRepository, mock(ProjectAccessGuard.class),
                new CostCalculator(),
                new SimulationResultMapper(), mock(AppLogger.class));

        // The fixtures carry ids for the unit tests; here the database assigns them.
        Version chain = SimulationFixtures.domesticChain("Base");
        List<BaseNode> nodes = new ArrayList<>();
        for (BaseNode node : chain.getNodeSnapshot()) {
            ReflectionTestUtils.setField(node, "id", null);
            node.setGraphData(new NodeGraphData(null, null));
            nodes.add(entityManager.persist(node));
        }
        List<NodeConnection> connections = List.of(
                entityManager.persist(new NodeConnection(UUID.randomUUID(), nodes.get(0).getId(), nodes.get(1).getId())),
                entityManager.persist(new NodeConnection(UUID.randomUUID(), nodes.get(1).getId(), nodes.get(2).getId())));
        Version version = entityManager.persist(
                new Version("Base", null, nodes, new ArrayList<>(connections), null, null));
        versionId = version.getId();
        entityManager.flush();
        entityManager.clear();
    }

    private long count(String table) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    @Test
    void aRunIsStoredWithItsPercentilesAndNodeCosts() {
        SimulationDetailDTO detail = service.runSimulation(versionId, new RunSimulationRequestDTO(1, 3));
        entityManager.flush();
        entityManager.clear();

        Version reloaded = versionRepository.findById(versionId).orElseThrow();
        assertEquals(1, reloaded.getFinalResults().size());
        FinalResult run = reloaded.getFinalResults().get(0);
        assertEquals(detail.summary().id(), run.getId());
        assertNotNull(run.getCreatedAt());
        assertEquals(3, run.getRounds());
        assertEquals(3, run.getPercentile10().size());
        assertEquals(3, run.getPercentile50().size());
        assertEquals(3, run.getPercentile90().size());
        assertEquals(3, run.getNodeCosts().size());
        assertEquals(new BigDecimal("3000.00"), run.getCapex());

        // The read model computed from the stored rows matches the one returned by the run.
        SimulationDetailDTO read = service.getSimulation(versionId, run.getId());
        assertEquals(detail.summary().kpis(), read.summary().kpis());
    }

    @Test
    void deletingARunRemovesEveryRowItOwns() {
        UUID runId = service.runSimulation(versionId, new RunSimulationRequestDTO(1, 2)).summary().id();
        entityManager.flush();
        assertEquals(3, count("node_cost_result"));

        service.deleteSimulation(versionId, runId);
        entityManager.flush();
        entityManager.clear();

        assertTrue(versionRepository.findById(versionId).orElseThrow().getFinalResults().isEmpty());
        assertEquals(0, count("final_result"));
        assertEquals(0, count("node_cost_result"));
        assertEquals(0, count("result_per_node"));
    }
}
