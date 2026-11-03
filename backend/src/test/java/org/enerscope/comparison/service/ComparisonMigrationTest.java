package org.enerscope.comparison.service;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonMigrationTest {
    @Test
    void migrationStoresSnapshotsAndEnforcesProjectReference() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE project(id UUID PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V24__scenario_comparisons.sql"));
            UUID project = UUID.randomUUID();
            statement.execute("INSERT INTO project VALUES('" + project + "')");
            try (var insert = connection.prepareStatement("INSERT INTO scenario_comparison(created_at,last_modified,project_id,name,configuration_json,result_json) VALUES(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?,?)")) {
                insert.setObject(1, project);
                insert.setString(2, "Comparison");
                insert.setString(3, "{\"weight\":100}");
                insert.setString(4, "{\"score\":50}");
                insert.executeUpdate();
                insert.setObject(1, UUID.randomUUID());
                assertThrows(java.sql.SQLException.class, insert::executeUpdate);
            }
            try (var rows = statement.executeQuery("SELECT configuration_json,result_json,active FROM scenario_comparison")) {
                assertTrue(rows.next());
                assertEquals("{\"weight\":100}", rows.getString(1));
                assertEquals("{\"score\":50}", rows.getString(2));
                assertTrue(rows.getBoolean(3));
                assertFalse(rows.next());
            }
        }
    }
}
