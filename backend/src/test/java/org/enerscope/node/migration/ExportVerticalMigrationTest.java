package org.enerscope.node.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.enerscope.node.model.enums.VerticalEnum;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class ExportVerticalMigrationTest {
    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL");
        connection.createStatement().execute("CREATE TABLE node_type_data (id INT PRIMARY KEY, vertical VARCHAR(20) NOT NULL, active BOOLEAN NOT NULL)");
        return connection;
    }
    private void migrate(Connection connection) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V15__normalize_export_vertical.sql"));
    }

    @Test void normalizesLegacyExportsWithoutChangingOtherNodeData() throws Exception {
        String legacy = "\u0415\u0425\u0420\u041ER\u0422";
        String[] values = {legacy, legacy, "EXPORT", "EXTRACTION", "TRANSPORTATION", "LIQUEFACTION", "INTERNAL_CONSUMPTION"};
        try (var connection = database()) {
            try (var insert = connection.prepareStatement("INSERT INTO node_type_data VALUES (?, ?, ?)")) {
                for (int i = 0; i < values.length; i++) {
                    insert.setInt(1, i); insert.setString(2, values[i]); insert.setBoolean(3, i != 1); insert.executeUpdate();
                }
            }
            migrate(connection);
            try (var rows = connection.createStatement().executeQuery("SELECT id, vertical, active FROM node_type_data ORDER BY id")) {
                for (int i = 0; i < values.length; i++) {
                    assertTrue(rows.next());
                    assertEquals(i, rows.getInt("id"));
                    assertEquals(i != 1, rows.getBoolean("active"));
                    String expected = i < 2 ? "EXPORT" : values[i];
                    assertEquals(expected, rows.getString("vertical"));
                    assertEquals(VerticalEnum.valueOf(expected), VerticalEnum.valueOf(rows.getString("vertical")));
                }
                assertFalse(rows.next());
            }
        }
    }

    @Test void migrationAcceptsFreshDatabaseWithoutNodes() throws Exception {
        try (var connection = database()) {
            migrate(connection);
            try (var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM node_type_data")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
        }
    }
}
