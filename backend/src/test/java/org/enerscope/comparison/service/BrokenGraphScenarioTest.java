package org.enerscope.comparison.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.enerscope.common.CsvUtil;
import org.enerscope.comparison.dto.ComparableScenarioDTO;
import org.enerscope.comparison.model.enums.ScenarioStatus;
import org.enerscope.economic.dto.EconomicJobRequest;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.economic.service.EconomicJobService;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.economic.service.EconomicSimulationAdapter;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.service.ProjectService;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.Readiness;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.SaveRequest;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.scenario.service.ScenarioService;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionNodeCsvImportService;
import org.enerscope.version.service.VersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A version whose graph the simulator cannot process must not break the rest of the project: the scenario lists answer
 * for every version and mark that one, and asking to simulate it is a controlled error (an
 * {@link IllegalArgumentException}, which the API answers with 400).
 *
 * <p>Real services and no test transaction, so a failure that marks a service transaction rollback-only would surface
 * here as it would in production.</p>
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:broken-graph;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class BrokenGraphScenarioTest {

    private static final String INVALID_CONNECTION = "The value chain graph has a connection the simulator cannot process";
    private static final String CARRIER_WITHOUT_TERMINAL =
            "An LNG carrier has no seaport terminal or FLNG unit connected before it";
    private static final String NOT_CHECKED = "The scenario could not be checked; review the server log";
    private static final String ECONOMICS = "{\"startYear\":2030,\"years\":2,\"currency\":\"USD\",\"wacc\":0.1,"
            + "\"taxEntities\":[{\"id\":\"e1\",\"name\":\"E1\",\"jurisdiction\":\"HYPOTHETICAL\",\"taxRate\":0.3,"
            + "\"carryLosses\":true,\"openingLoss\":0,\"lossExpiryYears\":null,\"lossOffsetLimit\":1,\"taxPaymentLagYears\":0}],"
            + "\"boundary\":[\"e1\"],\"adjustments\":[],\"contracts\":[],\"assets\":[],\"taxTreatments\":[],\"conversions\":[]}";

    /** The two shapes seen in production data: a failed cast while building, a null terminal while running. */
    enum Broken {
        WRONG_PREDECESSOR("WELL", "TREATMENT_PLANT", INVALID_CONNECTION),
        CARRIER_WITHOUT_SEAPORT("PIPELINE", "LNG_CARRIER", CARRIER_WITHOUT_TERMINAL);

        final String from;
        final String to;
        final String reason;

        Broken(String from, String to, String reason) {
            this.from = from;
            this.to = to;
            this.reason = reason;
        }
    }

    private record Kind(String nodeKind, NodeTypeEnum nodeType, Map<String, String> fields) {
    }

    private static Map<String, String> fields(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("WELL", NodeTypeEnum.WELL, fields(
                    "maxCollectionCapacity", "1000000", "declineCurve", "{\"type\":\"CONSTANT\",\"val_a\":0.25}",
                    "gasRichness", "0.85", "dtmTime", "0", "dtmCost", "500", "surface", "1200")),
            new Kind("GATHERING_NETWORK", NodeTypeEnum.GATHERING_NETWORK, fields(
                    "maxTransportCapacity", "1000", "length", "10", "lossPerMeter", "0.001", "connectedWells", "1")),
            new Kind("TREATMENT_PLANT", NodeTypeEnum.TREATMENT_PLANT, fields(
                    "maxTreatmentCapacity", "1000", "contaminantWaste", "0.01", "intermediateStorage", "100",
                    "treatmentCost", "10")),
            new Kind("PIPELINE", NodeTypeEnum.PIPELINE, fields(
                    "maxFlowCapacity", "800000", "length", "120", "lossPerKm", "0.001")),
            new Kind("PIPELINE_CONNECTION", NodeTypeEnum.PIPELINE_CONECTION, fields(
                    "transferCapacity", "1000", "outputPriority", "1")),
            new Kind("COMPRESSING_PLANT", NodeTypeEnum.COMPRESSING_PLANT, fields(
                    "maxCompressionCapacity", "1000", "processWaste", "0.01", "gasConsumption", "0.01")),
            new Kind("GROUND_BASED_LIQUEFACTION_PLANT", NodeTypeEnum.GROUND_LIQUEFACTION_PLANT, fields(
                    "maxProcessingCapacity", "1000", "mtpaRatio", "1", "intermediateStorage", "100",
                    "gasConsumption", "0.01")),
            new Kind("FLNG_UNIT", NodeTypeEnum.FLNG_UNIT, fields(
                    "maxProcessingCapacity", "1000", "mtpaRatio", "1", "intermediateStorage", "100",
                    "vesselDepth", "20", "hiringCost", "1000", "shipCapacity", "100", "gasConsumption", "0.01")),
            new Kind("LNG_CARRIER", NodeTypeEnum.LNG_CAMER, fields(
                    "exportFrequency", "1", "shipCapacity", "100", "fullLoadTime", "1", "hiringCost", "1000",
                    "timeToDestination", "1")),
            new Kind("SEAPORT_TERMINAL", NodeTypeEnum.SEAPORT_TERMINAL, fields(
                    "intermediateStorage", "100", "portDepth", "20", "shipCapacity", "100")),
            new Kind("INTERNAL_CONSUMPTION", NodeTypeEnum.INTERNAL_CONSUMPTION, fields(
                    "consumptionSummer", "10", "consumptionAutumn", "10", "consumptionWinter", "10",
                    "consumptionSpring", "10")),
            new Kind("INDUSTRIAL_CONSUMPTION", NodeTypeEnum.INDUSTRIAL_CONSUMPTION, fields("consumption", "75")));

    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;
    @Autowired ProjectService projects;
    @Autowired VersionService versions;
    @Autowired VersionNodeCsvImportService csvImport;
    @Autowired ScenarioStatusService statuses;
    @Autowired ScenarioService scenarioService;
    @Autowired ScenarioConfigurationService configurationService;
    @Autowired EconomicService economics;
    @Autowired EconomicJobService jobs;
    @Autowired EconomicInputCodec codec;
    @Autowired EconomicSimulationAdapter simulation;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private TransactionTemplate tx;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        var user = tx.execute(status -> {
            var persisted = new User("broken-graph-" + UUID.randomUUID() + "@example.com", "x", "Broken", "Graph",
                    PlatformRole.ADMIN);
            em.persist(persisted);
            return persisted;
        });
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        projectId = tx.execute(status -> {
            var organization = new Organization("Broken graph " + UUID.randomUUID());
            em.persist(organization);
            var project = new Project("Broken graph " + UUID.randomUUID(), "Test", organization);
            em.persist(project);
            return project.getId();
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers

    private static Map<String, String> node(String id, String kindName) {
        Kind kind = KINDS.stream().filter(k -> k.nodeKind().equals(kindName)).findFirst().orElseThrow();
        NodeTypeEnum type = kind.nodeType();
        var row = new LinkedHashMap<String, String>();
        row.put("recordType", "NODE");
        row.put("id", id);
        row.put("nodeKind", kindName);
        row.put("name", id);
        row.put("state", "RUNNING");
        row.put("lifespanInMonths", "240");
        row.put("upkeepCosts", "1000");
        row.put("maintenanceIntervalInDays", "30");
        row.put("operatingCosts", "5000");
        row.put("wastePercentage", "0.02");
        row.put("type", "{\"vertical\":\"" + type.vertical() + "\",\"role\":\"" + type.role() + "\",\"nodeType\":\""
                + type.name() + "\"}");
        row.put("investmentCost", "{\"components\":[]}");
        row.putAll(kind.fields());
        return row;
    }

    private static byte[] twoNodes(String from, String to, boolean withEconomics, String startupDate) {
        var records = new ArrayList<Map<String, String>>();
        var origin = node("origin", from);
        var target = node("target", to);
        if (startupDate != null) {
            origin.put("startupDate", startupDate);
            target.put("startupDate", startupDate);
        }
        records.add(origin);
        records.add(target);
        records.add(Map.of("recordType", "CONNECTION", "fromNodeId", "origin", "toNodeId", "target"));
        if (withEconomics) records.add(Map.of("recordType", "SCENARIO", "economicScenario", ECONOMICS));
        var header = new LinkedHashSet<>(List.of("recordType", "id", "fromNodeId", "toNodeId", "nodeKind", "economicScenario"));
        records.forEach(r -> header.addAll(r.keySet()));
        var rows = new ArrayList<List<String>>();
        rows.add(new ArrayList<>(header));
        records.forEach(r -> rows.add(header.stream().map(h -> r.getOrDefault(h, "")).toList()));
        return CsvUtil.write(rows).getBytes(StandardCharsets.UTF_8);
    }

    private UUID version(String from, String to) {
        UUID id = projects.saveVersion(projectId, new VersionDTO("v-" + UUID.randomUUID(), null)).getId();
        csvImport.importNodes(id, twoNodes(from, to, true, null));
        return id;
    }

    private UUID healthyVersion() {
        return version("TREATMENT_PLANT", "PIPELINE");
    }

    private UUID scenarioOf(UUID versionId) {
        return jdbc.queryForObject("select id from scenario where version_id = ? and is_default", UUID.class, versionId);
    }

    private Map<UUID, ComparableScenarioDTO> comparable() {
        return statuses.list(projectId).stream()
                .collect(Collectors.toMap(ComparableScenarioDTO::versionId, Function.identity()));
    }

    private Map<UUID, Readiness> readiness() {
        return configurationService.readiness(projectId).stream()
                .collect(Collectors.toMap(Readiness::versionId, Function.identity()));
    }

    // ---- lists

    @ParameterizedTest
    @EnumSource(Broken.class)
    void theComparisonScenarioListReturnsEveryVersionAndMarksOnlyTheBrokenOne(Broken broken) {
        UUID before = healthyVersion();
        UUID bad = version(broken.from, broken.to);
        UUID after = healthyVersion();

        var list = comparable();

        assertEquals(3, list.size());
        for (UUID healthy : List.of(before, after)) {
            assertTrue(list.get(healthy).ready(), () -> list.get(healthy).readinessErrors().toString());
        }
        assertFalse(list.get(bad).ready());
        assertEquals(List.of(broken.reason), list.get(bad).readinessErrors());
        assertEquals(ScenarioStatus.NEVER_SIMULATED, list.get(bad).status());
    }

    @ParameterizedTest
    @EnumSource(Broken.class)
    void simulationReadinessReturnsEveryVersionAndMarksOnlyTheBrokenOne(Broken broken) {
        UUID healthy = healthyVersion();
        UUID bad = version(broken.from, broken.to);

        var list = readiness();

        assertTrue(list.get(healthy).ready(), () -> list.get(healthy).errors().toString());
        assertFalse(list.get(bad).ready());
        assertEquals(List.of(broken.reason), list.get(bad).errors());
    }

    // ---- simulating it

    @ParameterizedTest
    @EnumSource(Broken.class)
    void askingToSimulateTheBrokenScenarioIsAControlledErrorWithItsReason(Broken broken) {
        UUID bad = version(broken.from, broken.to);

        var immediate = assertThrows(IllegalArgumentException.class, () -> economics.evaluate(projectId, bad));
        var queued = assertThrows(IllegalArgumentException.class,
                () -> jobs.submit(projectId, bad, new EconomicJobRequest(null, null)));

        assertEquals(broken.reason, immediate.getMessage());
        assertEquals(broken.reason, queued.getMessage());
        assertEquals(0, jdbc.queryForObject("select count(*) from economic_job where version_id = ?", Integer.class, bad));
    }

    /** The path the background worker takes: it never calls freeze again. */
    @ParameterizedTest
    @EnumSource(Broken.class)
    void runningTheSimulatorOnTheBrokenGraphThrowsOnlyAControlledError(Broken broken) throws Exception {
        UUID bad = version(broken.from, broken.to);
        var configuration = mapper.readValue(ECONOMICS, EconomicConfiguration.class);

        var error = assertThrows(IllegalArgumentException.class, () -> tx.execute(status ->
                simulation.simulate(em.find(Version.class, bad), configuration, 0L)));

        assertEquals(broken.reason, error.getMessage());
    }

    @ParameterizedTest
    @EnumSource(Broken.class)
    void savingTheConfigurationOfABrokenGraphKeepsItAsADraftWithTheReason(Broken broken) throws Exception {
        UUID bad = version(broken.from, broken.to);
        var current = configurationService.get(projectId, bad);
        var editor = mapper.readTree("{\"mode\":\"advanced\",\"value\":{\"taxEntities\":[],\"boundary\":[],"
                + "\"nodeProfiles\":[],\"adjustments\":[],\"contracts\":[],\"assets\":[],\"taxTreatments\":[],\"conversions\":[]}}");

        var saved = configurationService.save(projectId, bad, new SaveRequest(current.token(), current.sourceToken(),
                editor, mapper.valueToTree(current.configuration()), Map.of()));

        assertEquals(List.of(broken.reason), saved.errors());
        assertNotNull(saved.draft());
    }

    @Test
    void aSimulatedScenarioWhoseGraphLaterBreaksIsListedAsOutdated() {
        UUID simulated = healthyVersion();
        UUID healthy = healthyVersion();
        economics.evaluate(projectId, simulated);
        assertEquals(ScenarioStatus.UP_TO_DATE, comparable().get(simulated).status());
        var ids = tx.execute(status -> em.find(Version.class, simulated).getNodeSnapshot().stream()
                .collect(Collectors.toMap(BaseNode::getName, BaseNode::getId)));
        var connection = new ConnectionDTO();
        connection.setFromNodeId(ids.get("target"));
        connection.setToNodeId(ids.get("origin"));
        versions.addConnectionToVersion(simulated, connection); // PIPELINE -> TREATMENT_PLANT: a failed cast

        var list = comparable();

        assertEquals(ScenarioStatus.OUTDATED, list.get(simulated).status());
        assertEquals(List.of(INVALID_CONNECTION), list.get(simulated).readinessErrors());
        assertTrue(list.get(healthy).ready());
        var ofVersion = scenarioService.list(projectId, simulated);
        assertEquals(List.of(ScenarioStatus.OUTDATED), ofVersion.stream().map(s -> s.status()).toList());
    }

    // ---- a failure nobody foresaw, still per scenario

    @Test
    void anUnreadableStoredConfigurationMarksOnlyItsScenario() {
        UUID healthy = healthyVersion();
        UUID bad = healthyVersion();
        jdbc.update("update economic_configuration set configuration_json = '{not json' where scenario_id = ?", scenarioOf(bad));

        var list = comparable();
        var ready = readiness();

        assertTrue(list.get(healthy).ready());
        assertFalse(list.get(bad).ready());
        assertEquals(List.of(NOT_CHECKED), list.get(bad).readinessErrors());
        assertTrue(ready.get(healthy).ready());
        assertEquals(List.of(NOT_CHECKED), ready.get(bad).errors());
    }

    @Test
    void anUnreadableStoredSimulationMarksOnlyItsScenario() {
        UUID healthy = healthyVersion();
        UUID bad = healthyVersion();
        economics.evaluate(projectId, bad);
        jdbc.update("update economic_evaluation set snapshot_json = '{not json' where scenario_id = ?", scenarioOf(bad));

        var list = comparable();

        assertEquals(2, list.size());
        assertTrue(list.get(healthy).ready());
        assertFalse(list.get(bad).ready());
        assertEquals(List.of(NOT_CHECKED), list.get(bad).readinessErrors());
        assertNull(list.get(bad).latestEvaluationId(), "a result that cannot be read is not offered for comparison");
        assertFalse(list.get(bad).canSimulate());
        assertEquals(List.of(ScenarioStatus.OUTDATED),
                scenarioService.list(projectId, bad).stream().map(s -> s.status()).toList());
    }

    // ---- no over-rejection

    @Test
    void aCarrierThatNeverStartsWithinTheHorizonStillSimulates() {
        UUID id = projects.saveVersion(projectId, new VersionDTO("late carrier", null)).getId();
        csvImport.importNodes(id, twoNodes("PIPELINE", "LNG_CARRIER", true, "2099-01-01T00:00:00Z"));

        assertTrue(comparable().get(id).ready(), () -> comparable().get(id).readinessErrors().toString());
        assertNotNull(economics.evaluate(projectId, id).id());
    }

    /** Whatever two kinds are connected, building and running the graph never lets anything but a 400 out. */
    @Test
    void noPairOfNodeKindsLeaksAnUncontrolledExceptionFromTheSimulator() throws Exception {
        var configuration = mapper.readValue(ECONOMICS.replace("\"years\":2", "\"years\":1"), EconomicConfiguration.class);
        var leaks = new ArrayList<String>();
        for (Kind from : KINDS) {
            for (Kind to : KINDS) {
                UUID id = projects.saveVersion(projectId, new VersionDTO("pair-" + UUID.randomUUID(), null)).getId();
                csvImport.importNodes(id, twoNodes(from.nodeKind(), to.nodeKind(), false, null));
                try {
                    var input = tx.execute(status -> codec.freeze(em.find(Version.class, id), configuration, 1, 0L));
                    simulation.simulate(codec.thaw(input), configuration, 0L);
                } catch (IllegalArgumentException controlled) {
                    // 400 with its reason
                } catch (RuntimeException leak) {
                    leaks.add(from.nodeKind() + " -> " + to.nodeKind() + ": " + leak.getClass().getSimpleName());
                }
            }
        }
        assertEquals(List.of(), leaks);
    }
}
