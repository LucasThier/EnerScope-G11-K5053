package org.enerscope.node.migration;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.UUID;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class MigrationCatalogTest {
    @Test
    void versionedMigrationsHaveUniqueFlywayVersions() throws Exception {
        var versions = new HashSet<MigrationVersion>();
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql");
        assertTrue(resources.length > 0);
        for (var resource : resources) {
            String name = resource.getFilename();
            var version = MigrationVersion.fromVersion(name.substring(1, name.indexOf("__")));
            assertTrue(versions.add(version), "Duplicate migration: " + name);
        }
    }

    @Test
    void reconciledEnsureMigrationsPreserveExistingValues() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE base_node(id INT PRIMARY KEY, maintenance_duration INT NOT NULL)");
            statement.execute("INSERT INTO base_node VALUES(1,42)");
            statement.execute("CREATE TABLE probabilistic_distribution(id UUID PRIMARY KEY, distribution_type VARCHAR(30), val_a REAL, val_b REAL)");
            statement.execute("INSERT INTO probabilistic_distribution VALUES(RANDOM_UUID(),'CONSTANT',7,NULL)");
            for (var name : new String[]{"V20__ensure_probabilistic_distribution.sql", "V21__ensure_maintenance_duration.sql"}) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + name));
            }
            try (var rows = statement.executeQuery("SELECT maintenance_duration FROM base_node")) {
                assertTrue(rows.next()); assertEquals(42, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT val_a FROM probabilistic_distribution")) {
                assertTrue(rows.next()); assertEquals(7, rows.getInt(1)); assertFalse(rows.next());
            }
        }
    }
}
