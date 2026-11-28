package org.enerscope.scenario.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V26 (scenario table, "expand" only), V27 (unique per scenario instead of per version) and V28 (names kept in comparison
 * items) against real PostgreSQL, upgrading a disposable schema that
 * already holds rows at V25: every previous row must survive and end up linked to the default
 * scenario of its version, the constraints must hold, and running it again must change nothing.
 * Needs a disposable database in {@code ECONOMIC_TEST_DATABASE_URL} (its name must contain
 * {@code enerscope_economic_test}); it never touches an application schema.
 */
class ScenarioMigrationIT {
    static final String[] LINKED_TABLES = {"economic_configuration", "economic_draft", "scenario_draft",
            "economic_evaluation", "economic_job", "scenario_comparison_item"};

    String url, user, password, schema;
    Connection connection;
    Statement statement;
    Flyway flyway;        // up to V26 only
    Flyway toV27;         // up to V27
    Flyway latest;        // every migration (V28 included)

    final String org = id(), project = id(), comparison = id();
    // v1: everything; v2: one evaluation only; v3: inactive with a configuration; v4: nothing at all.
    final String v1 = id(), v2 = id(), v3 = id(), v4 = id();
    final String e1 = id(), e2 = id(), e3 = id();

    static String id() {
        return UUID.randomUUID().toString();
    }

    @BeforeEach
    void upgradeADatabaseThatAlreadyHoldsRowsAtV25() throws Exception {
        url = System.getenv("ECONOMIC_TEST_DATABASE_URL");
        user = System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_USER", "postgres");
        password = System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_PASSWORD", "");
        if (url == null || !url.contains("enerscope_economic_test")) {
            throw new IllegalStateException("Disposable ECONOMIC_TEST_DATABASE_URL required");
        }
        schema = "scenario_migration_" + UUID.randomUUID().toString().replace("-", "");
        connection = DriverManager.getConnection(url, user, password);
        statement = connection.createStatement();
        statement.execute("CREATE SCHEMA " + schema);
        statement.execute("SET search_path TO " + schema);

        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("25").load().migrate();
        previousRows();
        flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("26").load();
        toV27 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("27").load();
        latest = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).load();
    }

    @AfterEach
    void dropTheSchema() throws SQLException {
        statement.execute("SET search_path TO public");
        statement.execute("DROP SCHEMA " + schema + " CASCADE");
        connection.close();
    }

    /** Rows written by the application before V26 (no scenario_id exists yet). */
    void previousRows() throws SQLException {
        statement.execute("INSERT INTO organization(id, created_at, last_modified, name) VALUES('" + org + "', now(), now(), 'Org')");
        statement.execute("INSERT INTO project(id, created_at, last_modified, name, description, organization_id) "
                + "VALUES('" + project + "', now(), now(), 'Proyecto', 'd', '" + org + "')");
        version(v1, true);
        version(v2, true);
        version(v3, false);
        version(v4, true);
        for (String version : new String[] {v1, v3}) {
            statement.execute("INSERT INTO economic_configuration(id, created_at, last_modified, version_id, configuration_json) "
                    + "VALUES('" + id() + "', now(), now(), '" + version + "', '{}')");
        }
        statement.execute("INSERT INTO economic_draft(id, created_at, last_modified, version_id, draft_json) "
                + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '{}')");
        statement.execute("INSERT INTO scenario_draft(id, created_at, last_modified, version_id, draft_json) "
                + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '{}')");
        evaluation(e1, v1);
        evaluation(e2, v1);
        evaluation(e3, v2);
        job(v1, e1, "COMPLETED");
        job(v1, null, "QUEUED");
        statement.execute("INSERT INTO scenario_comparison(id, created_at, last_modified, project_id, name, relevance_threshold, rate_threshold) "
                + "VALUES('" + comparison + "', now(), now(), '" + project + "', 'v1 vs v2', 0.05, 0.01)");
        statement.execute("INSERT INTO scenario_comparison_item(id, created_at, last_modified, comparison_id, version_id, evaluation_id, position) "
                + "VALUES('" + id() + "', now(), now(), '" + comparison + "', '" + v1 + "', '" + e1 + "', 0),"
                + "('" + id() + "', now(), now(), '" + comparison + "', '" + v2 + "', '" + e3 + "', 1)");
    }

    void version(String id, boolean active) throws SQLException {
        statement.execute("INSERT INTO version(id, active, created_at, last_modified, name, versions_id) "
                + "VALUES('" + id + "', " + active + ", now(), now(), 'Versión " + id.substring(0, 4) + "', '" + project + "')");
    }

    void evaluation(String id, String version) throws SQLException {
        statement.execute("INSERT INTO economic_evaluation(id, created_at, last_modified, version_id, snapshot_json) "
                + "VALUES('" + id + "', now(), now(), '" + version + "', '{}')");
    }

    void job(String version, String evaluation, String status) throws SQLException {
        statement.execute("INSERT INTO economic_job(id, created_at, last_modified, version_id, input_json, status, rounds, seed, evaluation_id) "
                + "VALUES('" + id() + "', now(), now(), '" + version + "', '{}', '" + status + "', 1, '1', "
                + (evaluation == null ? "NULL" : "'" + evaluation + "'") + ")");
    }

    long count(String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    Map<String, Long> rowsPerTable() throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : LINKED_TABLES) counts.put(table, count("SELECT count(*) FROM " + table));
        counts.put("version", count("SELECT count(*) FROM version"));
        return counts;
    }

    @Test
    void everyPreviousRowSurvivesAndPointsAtTheDefaultScenarioOfItsVersion() throws Exception {
        Map<String, Long> before = rowsPerTable();
        assertEquals(2, before.get("economic_configuration"));
        assertEquals(3, before.get("economic_evaluation"));
        assertEquals(2, before.get("economic_job"));
        assertEquals(2, before.get("scenario_comparison_item"));

        assertEquals(1, flyway.migrate().migrationsExecuted);

        assertEquals(before, rowsPerTable(), "no row may be lost or added");
        for (String table : LINKED_TABLES) {
            assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE scenario_id IS NULL"), table + " has unlinked rows");
            assertEquals(0, count("SELECT count(*) FROM " + table + " t JOIN scenario s ON s.id = t.scenario_id "
                    + "WHERE s.version_id <> t.version_id OR NOT s.is_default"), table + " points at another version's scenario");
        }
    }

    @Test
    void everyVersionGetsExactlyOneEscenarioBase() throws Exception {
        flyway.migrate();

        assertEquals(4, count("SELECT count(*) FROM scenario"));
        assertEquals(0, count("SELECT count(*) FROM version v WHERE (SELECT count(*) FROM scenario s "
                + "WHERE s.version_id = v.id AND s.is_default AND s.name = 'Escenario base') <> 1"));
        // A version without any economics (v4) and an inactive one (v3) get one too; the flag follows the version.
        assertEquals(1, count("SELECT count(*) FROM scenario WHERE version_id = '" + v4 + "' AND active"));
        assertEquals(1, count("SELECT count(*) FROM scenario WHERE version_id = '" + v3 + "' AND NOT active"));
    }

    @Test
    void aScenarioNameIsUniqueInItsVersionIgnoringCaseAmongActiveScenariosOnly() throws Exception {
        flyway.migrate();

        scenario(v1, "Ampliación", false);
        assertThrows(SQLException.class, () -> scenario(v1, "AMPLIACIÓN", false));
        assertThrows(SQLException.class, () -> scenario(v1, "escenario BASE", false));
        scenario(v2, "Ampliación", false);   // the same name in another version is fine
        statement.execute("UPDATE scenario SET active = FALSE WHERE version_id = '" + v1 + "' AND name = 'Ampliación'");
        scenario(v1, "ampliación", false);   // a deleted scenario frees its name
        assertThrows(SQLException.class, () -> statement.execute(
                "INSERT INTO scenario(created_at, last_modified, version_id, name) VALUES(now(), now(), '" + v1 + "', '   ')"));
    }

    @Test
    void aVersionCannotHaveTwoActiveDefaultScenarios() throws Exception {
        flyway.migrate();

        assertThrows(SQLException.class, () -> scenario(v1, "Otro base", true));
        statement.execute("UPDATE scenario SET active = FALSE WHERE version_id = '" + v1 + "' AND is_default");
        scenario(v1, "Otro base", true);
    }

    @Test
    void runningTheMigrationAgainChangesNothing() throws Exception {
        assertEquals(1, flyway.migrate().migrationsExecuted);
        Map<String, Long> afterFirst = rowsPerTable();
        long scenarios = count("SELECT count(*) FROM scenario");

        assertEquals(0, flyway.migrate().migrationsExecuted, "Flyway must see V26 as applied");

        // The script itself is repeatable too: executing it by hand a second time adds and moves nothing.
        String script = Files.readString(Path.of("src/main/resources/db/migration/V26__scenario_table_expand.sql"), StandardCharsets.UTF_8);
        statement.execute(script);

        assertEquals(afterFirst, rowsPerTable());
        assertEquals(scenarios, count("SELECT count(*) FROM scenario"));
        for (String table : LINKED_TABLES) {
            assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE scenario_id IS NULL"));
        }
    }

    @Test
    void theUniqueConstraintsOnVersionIdStayUntilV27() throws Exception {
        flyway.migrate();

        // E3 only expands: a second economic configuration for the same version is still refused.
        assertThrows(SQLException.class, () -> statement.execute(
                "INSERT INTO economic_configuration(id, created_at, last_modified, version_id, configuration_json) "
                        + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '{}')"));
    }

    // ------------------------------------------------------------------ V27

    String defaultScenario(String version) throws SQLException {
        try (var rows = statement.executeQuery("SELECT id FROM scenario WHERE version_id = '" + version + "' AND is_default")) {
            rows.next();
            return rows.getString(1);
        }
    }

    String secondScenario(String version) throws SQLException {
        String id = id();
        statement.execute("INSERT INTO scenario(id, created_at, last_modified, version_id, name, is_default) VALUES('" + id + "', now(), now(), '"
                + version + "', 'Ampliación', FALSE)");
        return id;
    }

    void configuration(String version, String scenario) throws SQLException {
        statement.execute("INSERT INTO economic_configuration(id, created_at, last_modified, version_id, scenario_id, configuration_json) "
                + "VALUES('" + id() + "', now(), now(), '" + version + "', '" + scenario + "', '{}')");
    }

    @Test
    void v27KeepsEveryRowOfAV26DatabaseAndLosesNoLink() throws Exception {
        flyway.migrate();
        Map<String, Long> afterV26 = rowsPerTable();
        long scenarios = count("SELECT count(*) FROM scenario");

        assertEquals(1, toV27.migrate().migrationsExecuted, "only V27 is left to apply");

        assertEquals(afterV26, rowsPerTable(), "no row may be lost or added");
        assertEquals(scenarios, count("SELECT count(*) FROM scenario"));
        for (String table : LINKED_TABLES) {
            assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE scenario_id IS NULL"), table);
        }
    }

    @Test
    void aSecondScenarioOfTheSameVersionCanHaveItsOwnConfigurationDraftAndScenarioDraft() throws Exception {
        toV27.migrate();
        String second = secondScenario(v1);

        configuration(v1, second);
        statement.execute("INSERT INTO economic_draft(id, created_at, last_modified, version_id, scenario_id, draft_json) "
                + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '" + second + "', '{}')");
        statement.execute("INSERT INTO scenario_draft(id, created_at, last_modified, version_id, scenario_id, draft_json) "
                + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '" + second + "', '{}')");

        assertEquals(2, count("SELECT count(*) FROM economic_configuration WHERE version_id = '" + v1 + "'"));
        assertEquals(2, count("SELECT count(*) FROM economic_draft WHERE version_id = '" + v1 + "'"));
        assertEquals(2, count("SELECT count(*) FROM scenario_draft WHERE version_id = '" + v1 + "'"));
    }

    @Test
    void aScenarioStillHasAtMostOneConfigurationDraftAndScenarioDraft() throws Exception {
        toV27.migrate();
        String first = defaultScenario(v1);   // v1 already holds all three, written before V26

        assertThrows(SQLException.class, () -> configuration(v1, first));
        assertThrows(SQLException.class, () -> statement.execute(
                "INSERT INTO economic_draft(id, created_at, last_modified, version_id, scenario_id, draft_json) "
                        + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '" + first + "', '{}')"));
        assertThrows(SQLException.class, () -> statement.execute(
                "INSERT INTO scenario_draft(id, created_at, last_modified, version_id, scenario_id, draft_json) "
                        + "VALUES('" + id() + "', now(), now(), '" + v1 + "', '" + first + "', '{}')"));
    }

    @Test
    void v27IsRepeatable() throws Exception {
        assertEquals(2, toV27.migrate().migrationsExecuted);
        Map<String, Long> afterFirst = rowsPerTable();

        assertEquals(0, toV27.migrate().migrationsExecuted, "Flyway must see V27 as applied");
        String script = Files.readString(Path.of("src/main/resources/db/migration/V27__scenario_unique_per_scenario.sql"), StandardCharsets.UTF_8);
        statement.execute(script);
        statement.execute(script);

        assertEquals(afterFirst, rowsPerTable());
        // Still one unique index per scenario and no leftover UNIQUE (version_id) constraint.
        assertEquals(3, count("SELECT count(*) FROM pg_indexes WHERE schemaname = '" + schema + "' AND indexname IN "
                + "('uq_economic_configuration_scenario', 'uq_economic_draft_scenario', 'uq_scenario_draft_scenario')"));
        assertEquals(0, count("SELECT count(*) FROM pg_constraint c WHERE c.contype = 'u' AND c.conrelid IN "
                + "('economic_configuration'::regclass, 'economic_draft'::regclass, 'scenario_draft'::regclass)"
                + " AND (SELECT array_agg(a.attname::text) FROM unnest(c.conkey) AS k(n) "
                + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.n) = ARRAY['version_id']"));
    }

    // ------------------------------------------------------------------ V28

    @Test
    void v28GivesEveryExistingComparisonItemTheCurrentNamesWithoutLosingARow() throws Exception {
        toV27.migrate();
        Map<String, Long> before = rowsPerTable();
        assertEquals(2, before.get("scenario_comparison_item"));

        assertEquals(1, latest.migrate().migrationsExecuted, "only V28 is left to apply");

        assertEquals(before, rowsPerTable(), "no row may be lost or added");
        assertEquals(0, count("SELECT count(*) FROM scenario_comparison_item WHERE version_name IS NULL OR scenario_name IS NULL"));
        assertEquals(0, count("SELECT count(*) FROM scenario_comparison_item i JOIN version v ON v.id = i.version_id "
                + "JOIN scenario s ON s.id = i.scenario_id WHERE i.version_name <> v.name OR i.scenario_name <> s.name"));
        assertEquals(2, count("SELECT count(*) FROM scenario_comparison_item WHERE scenario_name = 'Escenario base'"));
        // The evaluations the items point at are untouched.
        assertEquals(3, count("SELECT count(*) FROM economic_evaluation"));
    }

    @Test
    void aSavedItemKeepsItsNamesWhenTheScenarioIsRenamedAndTheVersionDeleted() throws Exception {
        latest.migrate();
        String keptName = "Versión " + v1.substring(0, 4);

        statement.execute("UPDATE scenario SET name = 'Renombrado', active = FALSE, is_default = FALSE WHERE version_id = '" + v1 + "'");
        statement.execute("UPDATE version SET name = 'Otro nombre', active = FALSE WHERE id = '" + v1 + "'");

        try (var rows = statement.executeQuery("SELECT i.version_name, i.scenario_name, e.id FROM scenario_comparison_item i "
                + "JOIN economic_evaluation e ON e.id = i.evaluation_id WHERE i.version_id = '" + v1 + "'")) {
            assertTrue(rows.next(), "the item still opens through its evaluation although its scenario and version are inactive");
            assertEquals(keptName, rows.getString(1));
            assertEquals("Escenario base", rows.getString(2));
        }
    }

    @Test
    void v28IsRepeatableAndNeverOverwritesAStoredName() throws Exception {
        assertEquals(3, latest.migrate().migrationsExecuted);
        statement.execute("UPDATE scenario_comparison_item SET scenario_name = 'Nombre guardado' WHERE version_id = '" + v2 + "'");
        statement.execute("UPDATE scenario SET name = 'Cambió después' WHERE version_id = '" + v2 + "'");
        Map<String, Long> afterFirst = rowsPerTable();

        assertEquals(0, latest.migrate().migrationsExecuted, "Flyway must see V28 as applied");
        String script = Files.readString(Path.of("src/main/resources/db/migration/V28__comparison_item_names.sql"), StandardCharsets.UTF_8);
        statement.execute(script);
        statement.execute(script);

        assertEquals(afterFirst, rowsPerTable());
        assertEquals(1, count("SELECT count(*) FROM scenario_comparison_item WHERE scenario_name = 'Nombre guardado'"));
        assertEquals(0, count("SELECT count(*) FROM scenario_comparison_item WHERE version_name IS NULL OR scenario_name IS NULL"));
    }

    void scenario(String version, String name, boolean isDefault) throws SQLException {
        statement.execute("INSERT INTO scenario(created_at, last_modified, version_id, name, is_default) VALUES(now(), now(), '"
                + version + "', '" + name + "', " + isDefault + ")");
    }
}
