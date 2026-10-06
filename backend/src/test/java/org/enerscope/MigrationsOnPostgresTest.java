package org.enerscope;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real Flyway migrations on an empty PostgreSQL and lets Hibernate
 * validate the entities against the result, exactly as the application does at
 * startup ({@code ddl-auto=validate}).
 *
 * <p>Every other test builds its schema from the entities on H2 with Flyway
 * switched off, so none of them can notice a migration that does not run, two
 * migrations that create the same table, or an entity that maps a column no
 * migration creates. All of those used to surface only when somebody started
 * the app against a fresh database. The context starting at all is the main
 * assertion; the test method adds the checks that startup does not make.</p>
 *
 * <p>Needs a Docker daemon. Without one it is skipped rather than failed, so
 * {@code mvn test} still works on a machine that cannot run containers; the CI
 * workflow fails the build if this class was skipped there.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class MigrationsOnPostgresTest {

    // The same image as backend/docker-compose.yml.
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Test
    void everyMigrationAppliesToAnEmptyPostgresAndTheEntitiesValidateAgainstIt() throws IOException {
        MigrationInfoService info = flyway.info();
        int migrationFiles = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/*.sql").length;

        assertTrue(migrationFiles > 0, "no migration files found on the classpath");
        assertEquals(0, info.pending().length, "migrations left pending");
        // A file whose name Flyway does not recognise is silently ignored, so it
        // would never show up as applied.
        assertEquals(migrationFiles, info.applied().length,
                "every .sql file in db/migration must be applied; a misnamed file is ignored by Flyway");
    }
}
