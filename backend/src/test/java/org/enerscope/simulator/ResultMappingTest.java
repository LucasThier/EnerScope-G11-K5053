package org.enerscope.simulator;

import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:resultmapping;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ResultMappingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID wellNodeId = UUID.randomUUID();
    private final UUID pipelineNodeId = UUID.randomUUID();
    private final UUID carrierNodeId = UUID.randomUUID();

    private Version version;
    private FinalResult finalResult;

    @BeforeEach
    void setUp() {
        version = new Version("Baseline", null, null, null, null, null);
        finalResult = new FinalResult(5);
        finalResult.setMediaOutput(42.5f);
        finalResult.setPercentile90(new ArrayList<>(List.of(
                new ResultPerNode(wellNodeId, "Well", 10f, 2f, 12f),
                new ResultPerNode(pipelineNodeId, "Pipeline", 8f, 0f, 8f))));
        finalResult.setPercentile50(new ArrayList<>(List.of(
                new ResultPerNode(carrierNodeId, "LNGCarrier", 6f, 1f, 7f))));
        finalResult.setPercentile10(new ArrayList<>());
        version.addResult(finalResult);

        entityManager.persist(version);
        sync();
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void aFinalResultIsStoredAndLoadedBackWithItsPercentilesAndValues() {
        FinalResult loaded = entityManager.find(FinalResult.class, finalResult.getId());

        assertEquals(5, loaded.getTime());
        assertEquals(42.5f, loaded.getMediaOutput());
        assertEquals(2, loaded.getPercentile90().size());
        assertEquals(1, loaded.getPercentile50().size());
        assertTrue(loaded.getPercentile10().isEmpty());
        ResultPerNode well = loaded.getPercentile90().stream()
                .filter(perNode -> perNode.getNodeID().equals(wellNodeId))
                .findFirst()
                .orElseThrow();
        assertEquals("Well", well.getNodeClass());
        assertEquals(10f, well.getTotalProduced());
        assertEquals(2f, well.getTotalDeferred());
        assertEquals(12f, well.getMaxPossibleProduced());
    }

    @Test
    void thePercentileListsAreStoredInTheirOwnJoinTables() {
        assertEquals(2, count("final_result_p90"));
        assertEquals(1, count("final_result_p50"));
        assertEquals(0, count("final_result_p10"));
        assertEquals(3, count("result_per_node"));
        List<UUID> owners = jdbc.queryForList("select final_result_id from final_result_p90", UUID.class);
        assertTrue(owners.stream().allMatch(finalResult.getId()::equals));
    }

    @Test
    void resultPerNodeRowsUseTheColumnsOfTheMigration() {
        Map<String, Object> row = jdbc.queryForMap(
                "select node_id, node_class, total_produced, total_deferred, max_possible_produced, extra "
                        + "from result_per_node where node_id = ?", carrierNodeId);

        assertEquals("LNGCarrier", row.get("NODE_CLASS"));
        assertEquals(6f, ((Number) row.get("TOTAL_PRODUCED")).floatValue());
        assertEquals(1f, ((Number) row.get("TOTAL_DEFERRED")).floatValue());
        assertEquals(7f, ((Number) row.get("MAX_POSSIBLE_PRODUCED")).floatValue());
    }

    @Test
    void resultPerNodeRowsStoreTheNodeIdInTheNodeIdColumn() {
        List<UUID> nodeIds = jdbc.queryForList("select node_id from result_per_node", UUID.class);

        assertEquals(Set.of(wellNodeId, pipelineNodeId, carrierNodeId), new HashSet<>(nodeIds));
    }

    @Test
    void theFinalResultRowKeepsItsTimeMediaOutputAndVersionColumns() {
        Map<String, Object> row = jdbc.queryForMap("select time, media_output, version_id from final_result");

        assertEquals(5, ((Number) row.get("TIME")).intValue());
        assertEquals(42.5f, ((Number) row.get("MEDIA_OUTPUT")).floatValue());
        assertEquals(version.getId(), row.get("VERSION_ID"));
    }

    @Test
    void noJoinTableOutsideTheMigrationsIsCreated() {
        Integer unexpected = jdbc.queryForObject(
                "select count(*) from information_schema.tables where upper(table_name) in "
                        + "('RESULT_RESULT_PER_NODES', 'FINAL_RESULT_PERCENTILE90', 'FINAL_RESULT_PERCENTILE50', "
                        + "'FINAL_RESULT_PERCENTILE10', 'VERSION_FINAL_RESULTS')",
                Integer.class);

        assertEquals(0, unexpected);
    }

    @Test
    void removingARowFromAPercentileListDeletesItsPerNodeRow() {
        FinalResult loaded = entityManager.find(FinalResult.class, finalResult.getId());

        loaded.getPercentile90().removeIf(perNode -> perNode.getNodeID().equals(pipelineNodeId));
        sync();

        assertEquals(1, count("final_result_p90"));
        assertEquals(2, count("result_per_node"));
        assertEquals(Set.of(wellNodeId, carrierNodeId),
                new HashSet<>(jdbc.queryForList("select node_id from result_per_node", UUID.class)));
    }

    @Test
    void deletingAVersionDeletesItsFinalResultsAndTheirRows() {
        entityManager.remove(entityManager.find(Version.class, version.getId()));
        sync();

        assertEquals(0, count("final_result"));
        assertEquals(0, count("final_result_p90"));
        assertEquals(0, count("final_result_p50"));
        assertEquals(0, count("result_per_node"));
    }
}
