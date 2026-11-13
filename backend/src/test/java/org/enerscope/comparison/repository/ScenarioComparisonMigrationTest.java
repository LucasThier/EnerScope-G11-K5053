package org.enerscope.comparison.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs V24 against an in-memory database in PostgreSQL mode, on top of minimal
 * stand-ins for the tables it references, and checks its constraints.
 */
class ScenarioComparisonMigrationTest {

    Connection connection;
    Statement statement;
    final String project = UUID.randomUUID().toString();
    final String version = UUID.randomUUID().toString();
    final String evaluation = UUID.randomUUID().toString();
    final String otherEvaluation = UUID.randomUUID().toString();
    final String comparison = UUID.randomUUID().toString();

    @BeforeEach
    void migrate() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL");
        statement = connection.createStatement();
        statement.execute("CREATE TABLE project(id UUID PRIMARY KEY)");
        statement.execute("CREATE TABLE version(id UUID PRIMARY KEY)");
        statement.execute("CREATE TABLE economic_evaluation(id UUID PRIMARY KEY)");
        statement.execute("INSERT INTO project VALUES('" + project + "')");
        statement.execute("INSERT INTO version VALUES('" + version + "')");
        statement.execute("INSERT INTO economic_evaluation VALUES('" + evaluation + "'),('" + otherEvaluation + "')");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V24__scenario_comparisons.sql"));
        statement.execute("INSERT INTO scenario_comparison(id, created_at, last_modified, project_id, name, relevance_threshold, rate_threshold) "
                + "VALUES('" + comparison + "', NOW(), NOW(), '" + project + "', 'Base vs B', 0.05, 0.01)");
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
    }

    void item(String evaluationId, int position) throws SQLException {
        statement.execute("INSERT INTO scenario_comparison_item(created_at, last_modified, comparison_id, version_id, evaluation_id, position) "
                + "VALUES(NOW(), NOW(), '" + comparison + "', '" + version + "', '" + evaluationId + "', " + position + ")");
    }

    @Test
    void storesAComparisonWithOrderedItemsAndDefaultsActiveAndIds() throws Exception {
        item(evaluation, 0);
        item(otherEvaluation, 1);
        try (var rows = statement.executeQuery("SELECT COUNT(*), MIN(CAST(active AS INT)) FROM scenario_comparison_item")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
            assertEquals(1, rows.getInt(2));
        }
    }

    @Test
    void rejectsRepeatedPositionsAndRepeatedEvaluationsInOneComparison() throws Exception {
        item(evaluation, 0);
        assertThrows(SQLException.class, () -> item(otherEvaluation, 0));
        assertThrows(SQLException.class, () -> item(evaluation, 1));
        assertThrows(SQLException.class, () -> item(otherEvaluation, -1));
    }

    @Test
    void rejectsUnknownReferencesAndNonPositiveThresholds() {
        assertThrows(SQLException.class, () -> item(UUID.randomUUID().toString(), 0));
        assertThrows(SQLException.class, () -> statement.execute(
                "INSERT INTO scenario_comparison(created_at, last_modified, project_id, name, relevance_threshold, rate_threshold) "
                        + "VALUES(NOW(), NOW(), '" + project + "', 'Cero', 0, 0.01)"));
        assertThrows(SQLException.class, () -> statement.execute(
                "INSERT INTO scenario_comparison(created_at, last_modified, project_id, name, relevance_threshold, rate_threshold) "
                        + "VALUES(NOW(), NOW(), '" + UUID.randomUUID() + "', 'Huérfana', 0.05, 0.01)"));
    }
}
