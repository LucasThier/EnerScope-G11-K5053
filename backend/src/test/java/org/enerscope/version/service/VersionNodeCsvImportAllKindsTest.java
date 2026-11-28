package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.enerscope.common.CsvUtil;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.service.ProjectService;
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
 * One node of every kind the importer accepts goes through the real {@link VersionNodeCsvImportService} and the real
 * {@link VersionService} save (nothing mocked), so the importer's pre-check and the save rules
 * ({@code VersionService.alignDeclaredVertical}: vertical and role of {@link NodeTypeEnum}) are exercised together.
 * The {@code type} cell of each row is built from {@link NodeTypeEnum} itself, not from the importer's tables: a kind
 * whose pre-check disagrees with the enum (as INDUSTRIAL_CONSUMPTION did, EXPORT vs INTERNAL_CONSUMPTION) fails here.
 * Runs on H2 and, through {@code VersionNodeCsvImportAllKindsPostgreSqlIT}, on PostgreSQL.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:csv-all-kinds;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class VersionNodeCsvImportAllKindsTest {

    /** CSV {@code nodeKind} → the node type it creates, and the kind-specific columns with a valid value each. */
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

    private static final List<String> COMMON = List.of(
            "nodeKind", "name", "state", "lifespanInMonths", "upkeepCosts", "maintenanceIntervalInDays",
            "operatingCosts", "wastePercentage", "type", "investmentCost");

    @Autowired EntityManager em;
    @Autowired ProjectService projects;
    @Autowired VersionNodeCsvImportService csvImport;

    Project project;

    @BeforeEach
    void setUp() {
        var user = User.fromJwtClaims(UUID.randomUUID(), "csv-all-kinds@example.com", "Csv", "Kinds", PlatformRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var organization = new Organization("Csv all kinds");
        em.persist(organization);
        project = new Project("Csv all kinds", "Test", organization);
        em.persist(project);
        em.flush();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** {"vertical":…,"role":…,"nodeType":…} straight from the enum constant. */
    private static String typeJson(NodeTypeEnum nodeType) {
        return "{\"vertical\":\"" + nodeType.vertical() + "\",\"role\":\"" + nodeType.role()
                + "\",\"nodeType\":\"" + nodeType.name() + "\"}";
    }

    private static String csvWithOneNodeOfEachKind() {
        List<String> header = new ArrayList<>(COMMON);
        for (Kind kind : KINDS) {
            kind.fields().keySet().stream().filter(column -> !header.contains(column)).forEach(header::add);
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(header);
        for (Kind kind : KINDS) {
            Map<String, String> values = new LinkedHashMap<>(Map.of(
                    "nodeKind", kind.nodeKind(), "name", "Nodo " + kind.nodeKind(), "state", "RUNNING",
                    "lifespanInMonths", "240", "upkeepCosts", "1000", "maintenanceIntervalInDays", "30",
                    "operatingCosts", "5000", "wastePercentage", "0.02", "type", typeJson(kind.nodeType()),
                    "investmentCost", "{\"components\":[]}"));
            values.putAll(kind.fields());
            rows.add(header.stream().map(column -> values.getOrDefault(column, "")).toList());
        }
        return CsvUtil.write(rows);
    }

    @Test
    void theKindListCoversEveryNodeType() {
        assertEquals(NodeTypeEnum.values().length, KINDS.size());
        assertEquals(List.of(NodeTypeEnum.values()).stream().collect(Collectors.toSet()),
                KINDS.stream().map(Kind::nodeType).collect(Collectors.toSet()));
    }

    @Test
    void importsOneNodeOfEachOfTheTwelveKindsWithTheTypeOfNodeTypeEnum() {
        Version version = projects.saveVersion(project.getId(), new VersionDTO("Todos los tipos", null));
        em.flush();
        em.clear();

        int imported = csvImport.importNodes(version.getId(), csvWithOneNodeOfEachKind().getBytes(StandardCharsets.UTF_8));

        assertEquals(12, imported);
        em.flush();
        em.clear();
        Map<String, BaseNode> byName = em.find(Version.class, version.getId()).getNodeSnapshot().stream()
                .collect(Collectors.toMap(BaseNode::getName, Function.identity()));
        assertEquals(12, byName.size());
        for (Kind kind : KINDS) {
            BaseNode node = byName.get("Nodo " + kind.nodeKind());
            assertNotNull(node, kind.nodeKind() + " was not imported");
            assertEquals(kind.nodeType(), node.getType().getNodeType(), kind.nodeKind());
            assertEquals(kind.nodeType().vertical(), node.getType().getVertical(), kind.nodeKind());
            assertEquals(kind.nodeType().role(), node.getType().getRole(), kind.nodeKind());
        }
    }
}
