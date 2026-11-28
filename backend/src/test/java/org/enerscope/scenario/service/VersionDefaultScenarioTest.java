package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.comparison.model.enums.ScenarioStatus;
import org.enerscope.comparison.service.ScenarioStatusService;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.service.ProjectService;
import org.enerscope.scenario.dto.ScenarioNameRequest;
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
 * Every active version has, from the moment it is created, exactly one default scenario "Escenario base", empty: a root
 * version, a branch (which does NOT inherit its parent's scenarios or economics) and a detached version. Runs on H2, and on
 * PostgreSQL through {@code VersionDefaultScenarioPostgreSqlIT}, where the unique index on the default is real.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:version-default-scenario;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class VersionDefaultScenarioTest {
    @Autowired EntityManager em;
    @Autowired ProjectService projects;
    @Autowired org.enerscope.version.service.VersionService versions;
    @Autowired ScenarioService scenarioService;
    @Autowired ScenarioStatusService statuses;
    @Autowired ScenarioRepository scenarios;
    @Autowired EconomicConfigurationRepository configurations;

    Project project;

    @BeforeEach
    void setUp() {
        var user = User.fromJwtClaims(UUID.randomUUID(), "creation@example.com", "Version", "Creator", PlatformRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var organization = new Organization("Version default scenario");
        em.persist(organization);
        project = new Project("Version default scenario", "Test", organization);
        em.persist(project);
        em.flush();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    List<Scenario> scenariosOf(UUID versionId) {
        em.flush();
        return scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(versionId);
    }

    void assertExactlyTheEmptyBaseScenario(UUID versionId) {
        var found = scenariosOf(versionId);
        assertEquals(1, found.size(), "exactly one scenario");
        assertEquals("Escenario base", found.getFirst().getName());
        assertTrue(found.getFirst().isDefaultScenario());
        assertTrue(found.getFirst().isActive());
        assertTrue(configurations.scenarioIdsWithConfiguration(List.of(found.getFirst().getId())).isEmpty(), "and it is empty");
    }

    @Test
    void creatingARootVersionCreatesExactlyOneEmptyDefaultScenario() {
        Version root = projects.saveVersion(project.getId(), new VersionDTO("Raíz", null));

        assertExactlyTheEmptyBaseScenario(root.getId());
    }

    @Test
    void branchingCreatesItsOwnEmptyBaseScenarioAndInheritsNothingFromTheParent() {
        Version parent = projects.saveVersion(project.getId(), new VersionDTO("Padre", null));
        // The parent gets a second scenario and an economic configuration of its own.
        var extra = scenarioService.create(project.getId(), parent.getId(), new ScenarioNameRequest("Ampliación"));
        var parentBase = scenariosOf(parent.getId()).getFirst();
        var configuration = new EconomicConfigurationEntity(parent, "{}");
        configuration.assignScenario(parentBase);
        em.persist(configuration);
        em.flush();

        Version child = projects.saveVersion(project.getId(), new VersionDTO("Hija", parent.getId()));

        assertExactlyTheEmptyBaseScenario(child.getId());
        assertNotEquals(parentBase.getId(), scenariosOf(child.getId()).getFirst().getId(), "its own scenario, not the parent's");
        assertEquals(2, scenariosOf(parent.getId()).size(), "the parent keeps its two scenarios");
        assertTrue(scenariosOf(parent.getId()).stream().anyMatch(s -> s.getId().equals(extra.id())));
        assertEquals(1, configurations.scenarioIdsWithConfiguration(scenariosOf(parent.getId()).stream().map(Scenario::getId).toList()).size(),
                "and its configuration; the child has none");
    }

    @Test
    void aDetachedVersionAlsoGetsItsBaseScenario() {
        Version detached = versions.saveOrphanVersion(new VersionDTO("Suelta", null));

        assertExactlyTheEmptyBaseScenario(detached.getId());
    }

    @Test
    void severalVersionsEachGetTheirOwnSingleScenario() {
        Version first = projects.saveVersion(project.getId(), new VersionDTO("Primera", null));
        Version second = projects.saveVersion(project.getId(), new VersionDTO("Segunda", null));
        Version branch = projects.saveVersion(project.getId(), new VersionDTO("Rama", first.getId()));

        for (Version created : List.of(first, second, branch)) assertExactlyTheEmptyBaseScenario(created.getId());
        assertEquals(3, scenarios.count());
    }

    @Test
    void deletingAVersionRowAlsoRemovesItsScenariosAndLeavesTheOthersAlone() {
        // DELETE of a version is a physical delete today (VersionService.deleteVersion): its scenarios go with it.
        Version keep = projects.saveVersion(project.getId(), new VersionDTO("Queda", null));
        Version gone = projects.saveVersion(project.getId(), new VersionDTO("Se va", null));
        em.flush();
        em.clear();   // as in a real request: the project that lists the version is not managed while it is deleted

        versions.deleteVersion(gone.getId());
        em.flush();
        em.clear();

        assertTrue(scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(gone.getId()).isEmpty());
        assertEquals(1, scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(keep.getId()).size());
    }

    @Test
    void theComparisonPickerListsTheNewVersionsScenario() {
        Version root = projects.saveVersion(project.getId(), new VersionDTO("Raíz", null));
        Version branch = projects.saveVersion(project.getId(), new VersionDTO("Hija", root.getId()));
        em.flush();
        em.refresh(project);

        var listed = statuses.list(project.getId());

        assertEquals(2, listed.size());
        assertEquals(List.of("Raíz", "Hija"), listed.stream().map(s -> s.versionName()).toList());
        for (var entry : listed) {
            assertEquals("Escenario base", entry.scenarioName());
            assertTrue(entry.defaultScenario());
            assertEquals(ScenarioStatus.NEVER_SIMULATED, entry.status());
            assertFalse(entry.ready(), "an empty scenario cannot be simulated yet");
        }
        assertEquals(branch.getId(), listed.get(1).versionId());
    }
}
