package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.node.model.BaseNode;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.service.ProjectService;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two example files the frontend offers on «Importar CSV» ({@code plantilla-nodos.csv} and
 * {@code plantilla-nodos-con-economia.csv}, copied byte for byte, BOM included, from what
 * {@code frontend/src/utils/versionCsvImport.ts} generates into {@code src/test/resources/csv-templates}) go through the real
 * {@link VersionNodeCsvImportService} into an empty version. If the frontend templates change, regenerate the copies.
 * Runs on H2 and, through {@code VersionNodeCsvImportTemplatesPostgreSqlIT}, on PostgreSQL.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:csv-templates;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class VersionNodeCsvImportTemplatesTest {

    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;
    @Autowired ProjectService projects;
    @Autowired VersionNodeCsvImportService csvImport;
    @Autowired EconomicConfigurationRepository configurations;
    @Autowired ScenarioRepository scenarios;

    Project project;

    @BeforeEach
    void setUp() {
        var user = User.fromJwtClaims(UUID.randomUUID(), "csv-templates@example.com", "Csv", "Templates", PlatformRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var organization = new Organization("Csv templates");
        em.persist(organization);
        project = new Project("Csv templates", "Test", organization);
        em.persist(project);
        em.flush();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private byte[] template(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/csv-templates/" + name)) {
            assertNotNull(stream, name + " is missing from src/test/resources/csv-templates");
            return stream.readAllBytes();
        }
    }

    private Version emptyVersion(String name) {
        Version version = projects.saveVersion(project.getId(), new VersionDTO(name, null));
        em.flush();
        em.clear();
        var fresh = em.find(Version.class, version.getId());
        assertTrue(fresh.getNodeSnapshot().isEmpty(), "the version starts empty");
        assertTrue(fresh.getConnectionSnapshot().isEmpty());
        return fresh;
    }

    private Version reload(UUID versionId) {
        em.flush();
        em.clear();
        return em.find(Version.class, versionId);
    }

    private Scenario baseScenario(UUID versionId) {
        return scenarios.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(versionId).orElseThrow();
    }

    @Test
    void theNodesTemplateImportsOneWellAndNoConnectionsNorEconomics() throws IOException {
        Version version = emptyVersion("Plantilla de nodos");

        int imported = csvImport.importNodes(version.getId(), template("plantilla-nodos.csv"));

        assertEquals(1, imported);
        Version after = reload(version.getId());
        assertEquals(List.of("Pozo Norte"), after.getNodeSnapshot().stream().map(BaseNode::getName).toList());
        assertTrue(after.getConnectionSnapshot().isEmpty());
        assertTrue(configurations.findByScenarioId(baseScenario(version.getId()).getId()).isEmpty(),
                "no SCENARIO row: the Escenario base keeps no configuration");
        assertEquals(1, scenarios.countByVersionIdAndActiveTrue(version.getId()));
    }

    @Test
    void theEconomicsTemplateImportsTwoNodesOneConnectionAndTheEconomicsOfTheBaseScenario() throws IOException {
        Version version = emptyVersion("Plantilla con economía");

        int imported = csvImport.importNodes(version.getId(), template("plantilla-nodos-con-economia.csv"));

        assertEquals(2, imported, "the count is of nodes; the connection and the SCENARIO row are not counted");
        Version after = reload(version.getId());
        var byName = after.getNodeSnapshot().stream().collect(Collectors.toMap(BaseNode::getName, BaseNode::getId));
        assertEquals(Set.of("Pozo Norte", "Gasoducto 1"), byName.keySet());

        assertEquals(1, after.getConnectionSnapshot().size());
        var connection = after.getConnectionSnapshot().getFirst();
        assertEquals(byName.get("Pozo Norte"), connection.getFromNodeId());
        assertEquals(byName.get("Gasoducto 1"), connection.getToNodeId());

        Scenario base = baseScenario(version.getId());
        assertEquals(Scenario.DEFAULT_NAME, base.getName());
        assertEquals(1, scenarios.countByVersionIdAndActiveTrue(version.getId()), "no scenario was created besides the base one");
        var stored = configurations.findByScenarioId(base.getId()).orElseThrow(
                () -> new AssertionError("the economics did not land in the Escenario base"));
        EconomicConfiguration configuration = mapper.readValue(stored.getConfigurationJson(), EconomicConfiguration.class);
        assertEquals(2030, configuration.startYear());
        assertEquals(5, configuration.years());
        assertEquals("USD", configuration.currency());
        assertEquals(Set.of("empresa-1"), Set.copyOf(configuration.boundary()));
        assertEquals(Set.copyOf(byName.values()),
                configuration.nodeProfiles().stream().map(profile -> profile.nodeId()).collect(Collectors.toSet()),
                "every imported node got its economic profile, bound to the id the backend generated");
        assertEquals(1, configurations.findAllByVersionId(version.getId()).size());
    }
}
