package org.enerscope.simulator;

import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the JPA mapping of the simulation results to the tables Flyway creates in
 * {@code V8__create_results.sql}. Production runs Hibernate in {@code validate}
 * mode against the migrated PostgreSQL schema, but these tests build their H2
 * schema from the entities, so the column and table names are asserted here
 * directly: if an annotation drifts away from the migration, the application
 * stops starting and nothing else in the suite would notice.
 *
 * <p>{@code year} is a reserved word in H2 2.x, so Hibernate cannot create the
 * {@code result} table on a stock H2 URL (it is a plain identifier in
 * PostgreSQL). This class therefore keeps the {@code test} profile's own
 * datasource ({@code Replace.NONE}) and adds {@code NON_KEYWORDS=YEAR} to it.</p>
 */
@DataJpaTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:resultmapping;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=YEAR")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ResultMappingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID wellNodeId = UUID.randomUUID();
    private final UUID pipelineNodeId = UUID.randomUUID();

    private Version version;
    private Result result;

    @BeforeEach
    void setUp() {
        version = new Version("Baseline", null, null, null, null, null);
        result = new Result(2030);
        result.addAllResultPerNodes(List.of(
                new ResultPerNode(wellNodeId, "Well", 10f, 2f, 12f),
                new ResultPerNode(pipelineNodeId, "Pipeline", 8f, 0f, 8f)));
        version.addResult(result);

        // Saving the version cascades to its results and their per-node rows.
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
    void resultPerNodesAreStoredAndLoadedBackWithTheirValues() {
        Result loaded = entityManager.find(Result.class, result.getId());

        assertEquals(2030, loaded.getYear());
        assertEquals(2, loaded.getResultPerNodes().size());
        ResultPerNode well = loaded.getResultPerNodes().stream()
                .filter(perNode -> perNode.getNodeID().equals(wellNodeId))
                .findFirst()
                .orElseThrow();
        assertEquals("Well", well.getNodeClass());
        assertEquals(10f, well.getTotalProduced());
        assertEquals(2f, well.getTotalDeferred());
        assertEquals(12f, well.getMaxPossibleProduced());
    }

    @Test
    void resultPerNodeRowsReferenceTheirResultThroughResultId() {
        List<UUID> owners = jdbc.queryForList("select result_id from result_per_node", UUID.class);

        assertEquals(2, owners.size());
        assertTrue(owners.stream().allMatch(result.getId()::equals));
    }

    @Test
    void resultPerNodeRowsStoreTheNodeIdInTheNodeIdColumn() {
        List<UUID> nodeIds = jdbc.queryForList("select node_id from result_per_node", UUID.class);

        assertEquals(Set.of(wellNodeId, pipelineNodeId), new HashSet<>(nodeIds));
    }

    @Test
    void resultRowReferencesItsVersionThroughVersionId() {
        UUID owner = jdbc.queryForObject("select version_id from result", UUID.class);

        assertEquals(version.getId(), owner);
    }

    @Test
    void noJoinTableIsUsedForTheResultPerNodes() {
        Integer joinTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where upper(table_name) = 'RESULT_RESULT_PER_NODES'",
                Integer.class);

        assertEquals(0, joinTables);
    }

    @Test
    void removingAResultPerNodeFromItsResultDeletesItsRow() {
        Result loaded = entityManager.find(Result.class, result.getId());

        loaded.getResultPerNodes().removeIf(perNode -> perNode.getNodeID().equals(pipelineNodeId));
        sync();

        assertEquals(1, count("result_per_node"));
        assertEquals(List.of(wellNodeId),
                jdbc.queryForList("select node_id from result_per_node", UUID.class));
    }

    @Test
    void deletingAVersionDeletesItsResultsAndTheirRows() {
        entityManager.remove(entityManager.find(Version.class, version.getId()));
        sync();

        assertEquals(0, count("result"));
        assertEquals(0, count("result_per_node"));
    }
}
