package org.enerscope.economic.service;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Runs the joint save, draft and map conflict contract against real migrations and PostgreSQL. */
@SpringBootTest(properties={"economic.worker.enabled=false","spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate"})
class ScenarioConfigurationPostgreSqlIT extends ScenarioConfigurationTest {
 @org.junit.jupiter.api.Test void baselineIsIgnoredForExistingHistoryAndUpgradePreservesData() throws Exception {
  String url=System.getenv("ECONOMIC_TEST_DATABASE_URL"), user=System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_USER","postgres"), password=System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_PASSWORD","");
  String schema="scenario_upgrade_"+java.util.UUID.randomUUID().toString().replace("-","");
  try(var connection=java.sql.DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
   statement.execute("CREATE SCHEMA "+schema);
   try {
    statement.execute("SET search_path TO "+schema);
    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/migration/B18__initial_schema.sql"));
    statement.execute("CREATE TABLE preservation_probe (value integer)");statement.execute("INSERT INTO preservation_probe VALUES (42)");
    // This baseline builds an existing-history fixture in a disposable schema, never a real application database.
    var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).baselineVersion("18").load();
    flyway.baseline();
    org.junit.jupiter.api.Assertions.assertEquals(1,flyway.migrate().migrationsExecuted);
    org.junit.jupiter.api.Assertions.assertEquals(0,flyway.migrate().migrationsExecuted);
    try(var rows=statement.executeQuery("SELECT value FROM preservation_probe")) { org.junit.jupiter.api.Assertions.assertTrue(rows.next());org.junit.jupiter.api.Assertions.assertEquals(42,rows.getInt(1)); }
    try(var rows=statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE type='SQL_BASELINE'")) {rows.next();org.junit.jupiter.api.Assertions.assertEquals(0,rows.getInt(1));}
   } finally {statement.execute("SET search_path TO public");statement.execute("DROP SCHEMA "+schema+" CASCADE");}
  }
 }
 @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
  String url=System.getenv("ECONOMIC_TEST_DATABASE_URL");
  if(url==null || !url.contains("enerscope_economic_test"))throw new IllegalStateException("Disposable ECONOMIC_TEST_DATABASE_URL required");
  r.add("spring.datasource.url",()->url);
  r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
  r.add("spring.datasource.username",()->System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_USER","postgres"));
  r.add("spring.datasource.password",()->System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_PASSWORD",""));
 }
}
