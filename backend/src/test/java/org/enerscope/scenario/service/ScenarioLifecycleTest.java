package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.enerscope.comparison.model.ScenarioComparison;
import org.enerscope.comparison.model.ScenarioComparisonItem;
import org.enerscope.comparison.repository.ScenarioComparisonRepository;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.organization.model.Organization;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.project.model.Project;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.SaveRequest;
import org.enerscope.scenario.dto.ScenarioNameRequest;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * The life of the scenarios of a version (create, rename, duplicate, delete, limit, names) through the real services,
 * as a platform administrator (permissions have their own matrix in {@code ScenarioApiHttpTest}). It also runs against
 * PostgreSQL ({@code ScenarioLifecyclePostgreSqlIT}), where the partial unique indexes are real.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:scenario-lifecycle;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class ScenarioLifecycleTest {
    @Autowired EntityManager em;
    @Autowired ScenarioService service;
    @Autowired ScenarioConfigurationService scenarios;
    @Autowired EconomicService economics;
    @Autowired ScenarioDefaults defaults;
    @Autowired ScenarioLimits limits;
    @Autowired org.enerscope.version.service.VersionService versions;
    @Autowired EconomicEvaluationRepository evaluations;
    @Autowired EconomicJobRepository jobs;
    @Autowired ScenarioComparisonRepository comparisons;
    @Autowired ObjectMapper mapper;

    UUID projectId, versionId;
    Well well;

    @BeforeEach
    void setUp() throws Exception {
        var user = User.fromJwtClaims(UUID.randomUUID(), "lifecycle@example.com", "Life", "Cycle", PlatformRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var source = EconomicExample.version();
        var nodes = new ArrayList<BaseNode>();
        var ids = new HashMap<String, String>();
        for (var original : source.getNodeSnapshot()) {
            Well node = (Well) original;
            String old = node.getId().toString();
            ReflectionTestUtils.setField(node, "id", null);
            node.setType(new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
            node.setIdentityId(UUID.randomUUID());
            node.setGraphData(new NodeGraphData(new GraphPosition(0d, 0d), null));
            node.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
            node.setUpkeepCosts(MoneyAmount.of(2));
            node.setOperatingCosts(MoneyAmount.of(3));
            node.setDTMCost(MoneyAmount.of(4));
            node.setDeclineCurve(new ConstantValue(0));
            em.persist(node);
            nodes.add(node);
            ids.put(old, node.getId().toString());
        }
        well = (Well) nodes.getFirst();
        var organization = new Organization("Lifecycle test");
        em.persist(organization);
        var version = new Version("Lifecycle", null, nodes, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        var project = new Project("Lifecycle", "Test", organization);
        project.addVersion(version);
        em.persist(project);
        em.flush();
        projectId = project.getId();
        versionId = version.getId();
        String json = mapper.writeValueAsString(EconomicExample.configuration());
        for (var entry : ids.entrySet()) json = json.replace(entry.getKey(), entry.getValue());
        economics.save(projectId, versionId, mapper.readValue(json, EconomicConfiguration.class));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static ScenarioNameRequest named(String name) {
        return new ScenarioNameRequest(name);
    }

    Scenario defaultScenario() {
        return defaults.existing(versionId).orElseThrow();
    }

    Version version() {
        return em.find(Version.class, versionId);
    }

    Scenario entity(UUID id) {
        return em.find(Scenario.class, id);
    }

    // ------------------------------------------------------------ create, names, limit

    @Test
    void createsAnEmptyNonDefaultScenarioNamedByThePersonAndListsThemOldestFirst() {
        var created = service.create(projectId, versionId, named("  Ampliación  "));

        assertEquals("Ampliación", created.name());
        assertFalse(created.defaultScenario());
        assertFalse(created.configured());
        assertFalse(created.draftPending());
        var listed = service.list(projectId, versionId);
        assertEquals(List.of("Escenario base", "Ampliación"), listed.stream().map(s -> s.name()).toList());
        assertTrue(listed.getFirst().defaultScenario());
        assertTrue(listed.getFirst().configured(), "the base scenario holds the configuration saved in setUp");
    }

    @Test
    void theFirstScenarioOfAVersionThatHasNoDefaultBecomesIt() {
        var bare = new Version("Bare", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(bare);
        em.find(Project.class, projectId).addVersion(bare);
        em.flush();

        var first = service.create(projectId, bare.getId(), named("Primero"));
        var second = service.create(projectId, bare.getId(), named("Segundo"));

        assertTrue(first.defaultScenario());
        assertFalse(second.defaultScenario());
    }

    @Test
    void aNameIsUniqueInTheVersionIgnoringCaseAndTheDefaultNameIsTakenToo() {
        service.create(projectId, versionId, named("Ampliación"));

        assertThrows(ScenarioNameTakenException.class, () -> service.create(projectId, versionId, named("AMPLIACIÓN")));
        assertThrows(ScenarioNameTakenException.class, () -> service.create(projectId, versionId, named("escenario BASE")));
        assertThrows(ScenarioNameTakenException.class, () -> service.create(projectId, versionId, named("  ampliación ")));
    }

    @Test
    void renamingChecksTheNameAgainstTheOthersButNotAgainstItself() {
        var other = service.create(projectId, versionId, named("Conservador"));
        var expansion = service.create(projectId, versionId, named("Ampliación"));

        assertThrows(ScenarioNameTakenException.class, () -> service.rename(projectId, expansion.id(), named("CONSERVADOR")));
        assertEquals("AMPLIACIÓN", service.rename(projectId, expansion.id(), named("AMPLIACIÓN")).name(), "only the case changes");
        assertEquals("Optimista", service.rename(projectId, other.id(), named("Optimista")).name());
    }

    @Test
    void aMissingBlankOrTooLongNameIsABadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, versionId, null));
        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, versionId, named(null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, versionId, named("   ")));
        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, versionId, named("x".repeat(Scenario.NAME_MAX + 1))));
        assertEquals(Scenario.NAME_MAX, service.create(projectId, versionId, named("x".repeat(Scenario.NAME_MAX))).name().length());
    }

    @Test
    void aVersionHoldsAtMostTwentyActiveScenariosAndADeletedOneFreesItsPlace() {
        UUID last = null;
        for (int i = 2; i <= limits.maxPerVersion(); i++) last = service.create(projectId, versionId, named("Escenario " + i)).id();
        assertEquals(20, service.list(projectId, versionId).size());

        var refused = assertThrows(ScenarioLimitReachedException.class, () -> service.create(projectId, versionId, named("Uno más")));
        assertTrue(refused.getMessage().contains("20"));
        assertThrows(ScenarioLimitReachedException.class, () -> service.duplicate(projectId, defaultScenario().getId(), named("Copia")));

        service.delete(projectId, last);
        assertEquals("Uno más", service.create(projectId, versionId, named("Uno más")).name());
    }

    @Test
    void aDeletedScenarioFreesItsName() {
        var temporary = service.create(projectId, versionId, named("Temporal"));
        service.delete(projectId, temporary.id());

        assertEquals("temporal", service.create(projectId, versionId, named("temporal")).name());
    }

    // ------------------------------------------------------------ duplicate

    @Test
    void duplicatingCopiesOnlyTheConfigurationAndLeavesTheSourceIntact() {
        var base = defaultScenario();
        var draftSource = service.create(projectId, versionId, named("Con borrador"));
        var invalid = scenarios.saveIn(version(), entity(draftSource.id()), invalidDraft(entity(draftSource.id())));
        assertNotNull(invalid.draft());
        var evaluation = evaluation(base);
        em.flush();
        EconomicConfiguration before = economics.configuration(base);
        long evaluationsBefore = evaluations.findByScenarioIdOrderByCreatedAtDesc(base.getId()).size();

        var copy = service.duplicate(projectId, base.getId(), named("Copia del base"));

        assertEquals("Copia del base", copy.name());
        assertFalse(copy.defaultScenario());
        assertTrue(copy.configured());
        assertFalse(copy.draftPending(), "a draft is never copied");
        assertEquals(before, economics.configuration(entity(copy.id())), "the copy holds the same economic configuration");
        assertEquals(before, economics.configuration(base), "the source configuration is untouched");
        assertTrue(evaluations.findByScenarioIdOrderByCreatedAtDesc(copy.id()).isEmpty(), "simulations are not copied");
        assertEquals(evaluationsBefore, evaluations.findByScenarioIdOrderByCreatedAtDesc(base.getId()).size());
        assertTrue(evaluations.findById(evaluation.getId()).isPresent());
        assertTrue(service.get(projectId, draftSource.id()).draftPending(), "other scenarios keep their drafts");
    }

    @Test
    void duplicatingAnEmptyScenarioGivesAnEmptyOne() {
        var empty = service.create(projectId, versionId, named("Vacío"));

        var copy = service.duplicate(projectId, empty.id(), named("Copia vacía"));

        assertFalse(copy.configured());
        assertThrows(ScenarioNameTakenException.class, () -> service.duplicate(projectId, empty.id(), named("vacío")));
    }

    // ------------------------------------------------------------ delete

    @Test
    void theLastScenarioOfAVersionCannotBeDeleted() {
        var error = assertThrows(ScenarioDeletionRefusedException.class, () -> service.delete(projectId, defaultScenario().getId()));

        assertTrue(error.getMessage().contains("único escenario"));
        assertTrue(defaultScenario().isActive());
    }

    @Test
    void aScenarioWithAQueuedOrRunningSimulationCannotBeDeleted() {
        var busy = service.create(projectId, versionId, named("Ocupado"));
        for (EconomicJobStatus status : List.of(EconomicJobStatus.QUEUED, EconomicJobStatus.RUNNING)) {
            var job = new EconomicJob(version(), "{}", 1, "1");
            ReflectionTestUtils.setField(job, "status", status);
            job.assignScenario(entity(busy.id()));
            em.persist(job);
            em.flush();

            var error = assertThrows(ScenarioDeletionRefusedException.class, () -> service.delete(projectId, busy.id()));
            assertTrue(error.getMessage().contains("simulación"));
            ReflectionTestUtils.setField(job, "status", EconomicJobStatus.COMPLETED);
            em.flush();
        }

        service.delete(projectId, busy.id());
        assertFalse(entity(busy.id()).isActive());
    }

    @Test
    void deletingTheDefaultPromotesTheOldestRemainingOneInTheSameTransaction() {
        var base = defaultScenario();
        var a = service.create(projectId, versionId, named("A"));
        var b = service.create(projectId, versionId, named("B"));

        service.delete(projectId, base.getId());
        em.flush();
        em.clear();

        assertFalse(entity(base.getId()).isActive());
        assertFalse(entity(base.getId()).isDefaultScenario(), "the old mark is removed");
        assertTrue(entity(a.id()).isDefaultScenario(), "the oldest remaining becomes the default");
        assertFalse(entity(b.id()).isDefaultScenario());
        assertEquals(a.id(), defaults.existing(versionId).orElseThrow().getId(), "the old endpoints now act on it");
        assertEquals(1, service.list(projectId, versionId).stream().filter(s -> s.defaultScenario()).count());
    }

    @Test
    void deletingANonDefaultScenarioLeavesTheDefaultAlone() {
        var base = defaultScenario();
        var other = service.create(projectId, versionId, named("Otro"));

        service.delete(projectId, other.id());

        assertEquals(base.getId(), defaultScenario().getId());
        assertEquals(1, service.list(projectId, versionId).size());
    }

    @Test
    void deletingKeepsItsEvaluationsJobsAndTheSavedComparisonsThatUsedIt() {
        var base = defaultScenario();
        var doomed = service.create(projectId, versionId, named("Se borra"));
        var doomedEntity = entity(doomed.id());
        var kept = evaluation(base);
        var used = evaluation(doomedEntity);
        var job = new EconomicJob(version(), "{}", 1, "1");
        ReflectionTestUtils.setField(job, "status", EconomicJobStatus.COMPLETED);
        job.assignScenario(doomedEntity);
        em.persist(job);
        var comparison = new ScenarioComparison(em.find(Project.class, projectId), "Base vs Se borra", null,
                new BigDecimal("0.05"), new BigDecimal("0.01"));
        var first = new ScenarioComparisonItem(comparison, version(), kept, 0);
        first.assignScenario(base);
        var second = new ScenarioComparisonItem(comparison, version(), used, 1);
        second.assignScenario(doomedEntity);
        comparison.addItem(first);
        comparison.addItem(second);
        comparisons.saveAndFlush(comparison);
        em.flush();

        service.delete(projectId, doomed.id());
        em.flush();
        em.clear();

        assertFalse(entity(doomed.id()).isActive());
        assertTrue(evaluations.findById(used.getId()).isPresent(), "the evaluation stays");
        assertEquals(doomed.id(), evaluations.findById(used.getId()).orElseThrow().getScenario().getId());
        assertEquals(1, jobs.findByScenarioIdOrderByCreatedAtDesc(doomed.id()).size(), "the job stays");
        var stored = comparisons.findByIdAndProjectIdAndActiveTrue(comparison.getId(), projectId).orElseThrow();
        assertEquals(2, stored.getItems().size(), "the saved comparison keeps both items");
        assertEquals(doomed.id(), stored.getItems().get(1).getScenario().getId());
    }

    // ------------------------------------------------------------ several scenarios side by side

    @Test
    void theOldEndpointsKeepActingOnTheDefaultScenarioWhenOthersExist() {
        var base = defaultScenario();
        var copy = service.duplicate(projectId, base.getId(), named("Copia"));
        var state = scenarios.viewOf(version(), entity(copy.id()));
        var input = request(state);
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.configuration()).put("wacc", 0.2);
        scenarios.saveIn(version(), entity(copy.id()), input);

        assertEquals(0, new BigDecimal("0.2").compareTo(economics.configuration(entity(copy.id())).wacc()));
        assertNotEquals(0, new BigDecimal("0.2").compareTo(economics.get(projectId, versionId).wacc()),
                "the version-level read still returns the default scenario's configuration");
        assertEquals(economics.configuration(base), economics.get(projectId, versionId));
    }

    @Test
    void aDraftOfAnotherScenarioDoesNotBlockTheDefaultOneNorItsReadiness() {
        var other = service.create(projectId, versionId, named("Con borrador"));
        var state = scenarios.viewOf(version(), entity(other.id()));
        scenarios.saveIn(version(), entity(other.id()), invalidDraft(entity(other.id())));

        assertTrue(scenarios.readiness(projectId).getFirst().ready(), "readiness is about the default scenario");
        assertNotNull(state);
        assertTrue(service.get(projectId, other.id()).draftPending());
        assertFalse(service.get(projectId, defaultScenario().getId()).draftPending());
    }

    @Test
    void editingANodeRemapsTheConfigurationOfEveryScenarioOfTheVersion() {
        var base = defaultScenario();
        var copy = entity(service.duplicate(projectId, base.getId(), named("Copia")).id());
        var dto = new org.enerscope.node.dto.WellDTO();
        dto.setName("Edited from the map");
        dto.setState(well.getState());
        dto.setStartupDate(well.getStartupDate());
        dto.setLifespanInMonths(well.getLifespanInMonths());
        dto.setMaintenanceIntervalInDays(well.getMaintenanceIntervalInDays());
        dto.setWastePercentage(well.getWastePercentage());
        dto.setGasRichness(well.getGasRichness());
        dto.setMaxCollectionCapacity(250f);
        dto.setUpkeepCosts(2f);
        dto.setOperatingCosts(3f);
        dto.setDTMCost(4f);

        var edited = versions.editNodeInVersion(versionId, well.getId(), dto);
        em.flush();
        em.clear();

        assertNotEquals(well.getId(), edited.getId(), "the inherited node was cloned");
        for (Scenario scenario : List.of(entity(base.getId()), entity(copy.getId()))) {
            var config = economics.configuration(scenario);
            assertTrue(config.conversions().stream().anyMatch(c -> c.nodeId().equals(edited.getId())),
                    scenario.getName() + " must reference the new node");
            assertFalse(config.conversions().stream().anyMatch(c -> c.nodeId().equals(well.getId())),
                    scenario.getName() + " must no longer reference the old node");
        }
    }

    @Test
    void editingANodeLeavesEveryScenarioOutdatedByDesign() {
        var base = defaultScenario();
        var copy = entity(service.duplicate(projectId, base.getId(), named("Copia")).id());
        // The status of a scenario depends on its configuration AND the version's nodes (see ScenarioStatusService):
        // the "source" token the screen reads changes for every scenario when a node changes.
        String baseBefore = scenarios.viewOf(version(), base).sourceToken();
        String copyBefore = scenarios.viewOf(version(), copy).sourceToken();
        well.setMaxCollectionCapacity(321);
        em.flush();

        assertNotEquals(baseBefore, scenarios.viewOf(version(), base).sourceToken());
        assertNotEquals(copyBefore, scenarios.viewOf(version(), copy).sourceToken());
    }

    @Test
    void eachScenarioReportsItsOwnStatusAndANodeEditOutdatesEveryOneThatWasSimulated() {
        var base = defaultScenario();
        var copy = service.duplicate(projectId, base.getId(), named("Copia"));
        assertEquals(org.enerscope.comparison.model.enums.ScenarioStatus.NEVER_SIMULATED, service.get(projectId, base.getId()).status());
        assertNull(service.get(projectId, base.getId()).latestEvaluatedAt());

        economics.evaluate(projectId, versionId);   // simulates the default scenario only
        em.flush();

        var simulated = service.get(projectId, base.getId());
        assertEquals(org.enerscope.comparison.model.enums.ScenarioStatus.UP_TO_DATE, simulated.status());
        assertNotNull(simulated.latestEvaluatedAt());
        assertEquals(org.enerscope.comparison.model.enums.ScenarioStatus.NEVER_SIMULATED, service.get(projectId, copy.id()).status());

        well.setMaxCollectionCapacity(321);   // a node of the version changes
        em.flush();

        assertEquals(org.enerscope.comparison.model.enums.ScenarioStatus.OUTDATED, service.get(projectId, base.getId()).status());
        assertEquals(org.enerscope.comparison.model.enums.ScenarioStatus.NEVER_SIMULATED, service.get(projectId, copy.id()).status(),
                "a scenario never simulated has nothing to be outdated");
    }

    @Test
    void aScenarioWithAQueuedOrRunningSimulationIsSimulating() {
        var busy = service.create(projectId, versionId, named("Ocupado"));
        var job = new EconomicJob(version(), "{}", 1, "1");
        job.assignScenario(entity(busy.id()));
        em.persist(job);
        em.flush();

        assertEquals(org.enerscope.comparison.model.enums.ScenarioStatus.SIMULATING, service.get(projectId, busy.id()).status());
    }

    // ------------------------------------------------------------ helpers

    EconomicEvaluation evaluation(Scenario scenario) {
        var evaluation = new EconomicEvaluation(version(), "{}");
        evaluation.assignScenario(scenario);
        em.persist(evaluation);
        return evaluation;
    }

    SaveRequest request(ScenarioConfigurationDTO state) {
        var edits = new HashMap<UUID, Map<String, JsonNode>>();
        state.nodes().forEach(n -> edits.put(n.id(), new TreeMap<>(n.values())));
        return new SaveRequest(state.token(), state.sourceToken(),
                mapper.valueToTree(Map.of("mode", "advanced", "value", state.configuration() == null ? Map.of() : state.configuration())),
                state.configuration() == null ? null : mapper.valueToTree(state.configuration()), edits);
    }

    /** An advanced-editor save with no configuration: it becomes the scenario's draft. */
    SaveRequest invalidDraft(Scenario scenario) {
        var state = scenarios.viewOf(version(), scenario);
        var editor = mapper.createObjectNode().put("mode", "advanced");
        var value = mapper.createObjectNode();
        for (String field : List.of("taxEntities", "boundary", "nodeProfiles", "adjustments", "contracts", "assets", "taxTreatments", "conversions")) {
            value.putArray(field);
        }
        editor.set("value", value);
        return new SaveRequest(state.token(), state.sourceToken(), editor, null, null);
    }
}
