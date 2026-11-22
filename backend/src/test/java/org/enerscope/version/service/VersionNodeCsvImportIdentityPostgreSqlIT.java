package org.enerscope.version.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The CSV identity-collision cases against real migrations and PostgreSQL, where the foreign keys are real. */
@SpringBootTest(properties = {"economic.worker.enabled=false", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class VersionNodeCsvImportIdentityPostgreSqlIT extends VersionNodeCsvImportIdentityTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECONOMIC_TEST_DATABASE_URL");
        if (url == null || !url.contains("enerscope_economic_test")) throw new IllegalStateException("Disposable ECONOMIC_TEST_DATABASE_URL required");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_PASSWORD", ""));
    }
}
