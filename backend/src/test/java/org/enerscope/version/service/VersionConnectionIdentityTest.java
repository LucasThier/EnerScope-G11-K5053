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
import java.util.stream.Collectors;
import org.enerscope.common.CsvUtil;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.model.BaseNode;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A connection {@code identity} that is repeated, or already in the version, is refused and writes nothing.
 *
 * <p>Deliberately NOT {@code @Transactional}: every call commits or rolls back on its own, as in production, and the
 * state is read back from the database afterwards. Runs on H2 and, through
 * {@code VersionConnectionIdentityPostgreSqlIT}, on PostgreSQL.</p>
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:connection-identity;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class VersionConnectionIdentityTest {

    @Autowired EntityManager em;
    @Autowired ProjectService projects;
    @Autowired VersionService versions;
    @Autowired VersionNodeCsvImportService csvImport;
    @Autowired PlatformTransactionManager transactions;

    private TransactionTemplate tx;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        var user = User.fromJwtClaims(UUID.randomUUID(), "connection-identity@example.com", "Connection", "Identity",
                PlatformRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        projectId = tx.execute(status -> {
            var organization = new Organization("Connection identity " + UUID.randomUUID());
            em.persist(organization);
            var project = new Project("Connection identity " + UUID.randomUUID(), "Test", organization);
            em.persist(project);
            return project.getId();
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ---- CSV helpers: three pipelines A, B, C (or the given names) plus the given extra records

    private static Map<String, String> pipeline(String id) {
        var row = new LinkedHashMap<String, String>();
        row.put("recordType", "NODE");
        row.put("id", id);
        row.put("nodeKind", "PIPELINE");
        row.put("name", id);
        row.put("state", "RUNNING");
        row.put("lifespanInMonths", "240");
        row.put("upkeepCosts", "1000");
        row.put("maintenanceIntervalInDays", "30");
        row.put("operatingCosts", "5000");
        row.put("wastePercentage", "0.02");
        row.put("type", "{\"vertical\":\"TRANSPORTATION\",\"role\":\"INTERMEDIATE\",\"nodeType\":\"PIPELINE\"}");
        row.put("investmentCost", "{\"components\":[]}");
        row.put("maxFlowCapacity", "800000");
        row.put("length", "120");
        row.put("lossPerKm", "0.001");
        return row;
    }

    private static Map<String, String> edge(String from, String to, UUID identity) {
        return Map.of("recordType", "CONNECTION", "fromNodeId", from, "toNodeId", to,
                "identity", identity == null ? "" : identity.toString());
    }

    private static byte[] csv(List<Map<String, String>> records) {
        List<String> header = new ArrayList<>(List.of("recordType", "id", "fromNodeId", "toNodeId", "identity",
                "economicScenario"));
        records.forEach(r -> r.keySet().stream().filter(k -> !header.contains(k)).forEach(header::add));
        List<List<String>> rows = new ArrayList<>();
        rows.add(header);
        records.forEach(r -> rows.add(header.stream().map(h -> r.getOrDefault(h, "")).toList()));
        return CsvUtil.write(rows).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] nodesAndEdges(List<String> nodes, List<Map<String, String>> edges) {
        var records = new ArrayList<Map<String, String>>();
        nodes.forEach(n -> records.add(pipeline(n)));
        records.addAll(edges);
        return csv(records);
    }

    private UUID newVersion(UUID parent) {
        return projects.saveVersion(projectId, new VersionDTO("v-" + UUID.randomUUID(), parent)).getId();
    }

    /** Everything a rejected write could have touched, as comparable text, plus the table-wide row counts. */
    private String state(UUID versionId) {
        return tx.execute(status -> {
            Version v = em.find(Version.class, versionId);
            var nodes = v.getNodeSnapshot().stream().map(n -> n.getName() + ":" + n.getId()).sorted().toList();
            var connections = v.getConnectionSnapshot().stream()
                    .map(c -> c.getId() + ":" + c.getIdentityId() + ":" + c.getFromNodeId() + ">" + c.getToNodeId())
                    .sorted().toList();
            var nodeChanges = v.getNodeChanges().stream().map(c -> c.getId().toString()).sorted().toList();
            var connectionChanges = v.getConnectionChanges().stream().map(c -> c.getId().toString()).sorted().toList();
            return "nodes=" + nodes + "\nconnections=" + connections + "\nnodeChanges=" + nodeChanges
                    + "\nconnectionChanges=" + connectionChanges
                    + "\nnodeRows=" + count("BaseNode") + " connectionRows=" + count("NodeConnection")
                    + " nodeChangeRows=" + count("NodeChange") + " connectionChangeRows=" + count("ConnectionChange");
        });
    }

    private long count(String entity) {
        return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }

    /** A version holding A -> B with the given identity, and the id of every node by name. */
    private UUID versionWithEdge(UUID identity) {
        UUID version = newVersion(null);
        csvImport.importNodes(version, nodesAndEdges(List.of("A", "B"), List.of(edge("A", "B", identity))));
        return version;
    }

    private Map<String, UUID> nodeIds(UUID versionId) {
        return tx.execute(status -> em.find(Version.class, versionId).getNodeSnapshot().stream()
                .collect(Collectors.toMap(BaseNode::getName, BaseNode::getId)));
    }

    private static ConnectionDTO dto(UUID from, UUID to, UUID identity) {
        var dto = new ConnectionDTO();
        dto.setFromNodeId(from);
        dto.setToNodeId(to);
        dto.setIdentity(identity);
        return dto;
    }

    // ---- CSV

    @ParameterizedTest(name = "second row {0} -> {1}")
    @CsvSource({"B,C", "A,B"})
    void csvWithTheSameConnectionIdentityInTwoRowsIsRefusedAndWritesNothing(String from, String to) {
        UUID identity = UUID.randomUUID();
        UUID version = newVersion(null);
        String before = state(version);

        var error = assertThrows(IllegalArgumentException.class, () -> csvImport.importNodes(version,
                nodesAndEdges(List.of("A", "B", "C"), List.of(edge("A", "B", identity), edge(from, to, identity)))));

        assertEquals("Invalid CSV record 6 (CONNECTION): identity " + identity + " is repeated in the file",
                error.getMessage());
        assertEquals(before, state(version));
    }

    @Test
    void csvWithAConnectionIdentityAlreadyInTheVersionIsRefusedAndKeepsTheExistingConnection() {
        UUID identity = UUID.randomUUID();
        UUID version = versionWithEdge(identity);
        String before = state(version);

        var error = assertThrows(IllegalArgumentException.class, () -> csvImport.importNodes(version,
                nodesAndEdges(List.of("C", "D"), List.of(edge("C", "D", identity)))));

        assertEquals("Invalid CSV record 4 (CONNECTION): identity " + identity + " already exists in this version",
                error.getMessage());
        assertEquals(before, state(version));
    }

    @Test
    void csvIntoABranchRefusesAnInheritedConnectionIdentityAndNeitherBranchNorParentChanges() {
        UUID identity = UUID.randomUUID();
        UUID parent = versionWithEdge(identity);
        UUID child = newVersion(parent);
        String parentBefore = state(parent);
        String childBefore = state(child);

        var error = assertThrows(IllegalArgumentException.class, () -> csvImport.importNodes(child,
                nodesAndEdges(List.of("C", "D"), List.of(edge("C", "D", identity)))));

        assertTrue(error.getMessage().endsWith("identity " + identity + " already exists in this version"));
        assertEquals(childBefore, state(child));
        assertEquals(parentBefore, state(parent));
    }

    @Test
    void csvMayReuseAConnectionIdentityThatOnlyAnUnrelatedVersionHas() {
        UUID identity = UUID.randomUUID();
        versionWithEdge(identity);

        UUID other = versionWithEdge(identity);

        assertEquals(List.of(identity), tx.execute(status -> em.find(Version.class, other).getConnectionSnapshot()
                .stream().map(c -> c.getIdentityId()).toList()));
    }

    /** The rejection above happens before any write; this one happens after nodes and connections were saved. */
    @Test
    void anImportThatFailsAfterWritingNodesAndConnectionsLeavesNoData() {
        UUID version = newVersion(null);
        String before = state(version);
        var records = new ArrayList<Map<String, String>>(
                List.of(pipeline("A"), pipeline("B"), edge("A", "B", UUID.randomUUID())));
        records.add(Map.of("recordType", "SCENARIO", "economicScenario",
                "{\"startYear\":2030,\"years\":5,\"currency\":\"USD\",\"wacc\":0.1,\"taxEntities\":[],\"boundary\":[],"
                        + "\"adjustments\":[],\"contracts\":[],\"assets\":[],\"taxTreatments\":[],\"conversions\":[]}"));

        assertThrows(IllegalArgumentException.class, () -> csvImport.importNodes(version, csv(records)));

        assertEquals(before, state(version));
    }

    // ---- the editor's JSON call

    @Test
    void addingAConnectionWithAnIdentityAlreadyInTheVersionIsRefusedAndWritesNothing() {
        UUID identity = UUID.randomUUID();
        UUID version = versionWithEdge(identity);
        var ids = nodeIds(version);
        String before = state(version);

        var error = assertThrows(IllegalArgumentException.class,
                () -> versions.addConnectionToVersion(version, dto(ids.get("B"), ids.get("A"), identity)));

        assertEquals("Connection identity " + identity + " already exists in this version", error.getMessage());
        assertEquals(before, state(version));
    }

    @Test
    void addingAConnectionToABranchRefusesAnInheritedIdentityAndTheMergeLosesNothing() {
        UUID identity = UUID.randomUUID();
        UUID parent = versionWithEdge(identity);
        UUID child = newVersion(parent);
        var ids = nodeIds(child);
        String childBefore = state(child);

        assertThrows(IllegalArgumentException.class,
                () -> versions.addConnectionToVersion(child, dto(ids.get("B"), ids.get("A"), identity)));
        assertEquals(childBefore, state(child));

        versions.mergeSubVersionIntoParent(child);
        var merged = tx.execute(status -> em.find(Version.class, parent).getConnectionSnapshot().stream()
                .map(c -> c.getIdentityId()).toList());
        assertEquals(List.of(identity), merged);
    }

    @Test
    void addingConnectionsWithoutAnIdentityStillGeneratesOneForEach() {
        UUID version = versionWithEdge(null);
        var ids = nodeIds(version);

        var first = versions.addConnectionToVersion(version, dto(ids.get("A"), ids.get("B"), null));
        var second = versions.addConnectionToVersion(version, dto(ids.get("A"), ids.get("B"), null));

        assertNotNull(first.getIdentityId());
        assertNotEquals(first.getIdentityId(), second.getIdentityId());
    }

    @Test
    void addingAConnectionWithANewExplicitIdentityKeepsIt() {
        UUID version = versionWithEdge(UUID.randomUUID());
        var ids = nodeIds(version);
        UUID identity = UUID.randomUUID();

        var saved = versions.addConnectionToVersion(version, dto(ids.get("B"), ids.get("A"), identity));

        assertEquals(identity, saved.getIdentityId());
    }
}
