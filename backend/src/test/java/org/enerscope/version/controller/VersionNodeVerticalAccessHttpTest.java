package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VersionNodeVerticalAccessHttpTest {

    private static final String VERTICAL_DENIED = "You are not allowed to edit this vertical's nodes in this version";
    private static final String MISMATCH_PREFIX = "Node vertical does not match its type";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;

    private AccessHttpFixture fixture;

    record NodeKind(NodeTypeEnum type, String role, String fields) {
        @Override
        public String toString() {
            return type.name();
        }
    }

    private static final NodeKind WELL = new NodeKind(NodeTypeEnum.WELL, "GENERATOR",
            "\"maxCollectionCapacity\":1000,\"declineCurve\":{\"type\":\"CONSTANT\",\"val_a\":0.05},"
                    + "\"gasRichness\":0.3,\"dtmTime\":15,\"dtmCost\":150,\"surface\":10");
    private static final NodeKind LNG_CARRIER = new NodeKind(NodeTypeEnum.LNG_CAMER, "SINK",
            "\"exportFrequency\":4,\"shipCapacity\":100,\"fullLoadTime\":2,\"hiringCost\":1000,\"timeToDestination\":10");

    static List<NodeKind> everyCreatableType() {
        return List.of(
                WELL,
                new NodeKind(NodeTypeEnum.GATHERING_NETWORK, "INTERMEDIATE",
                        "\"maxTransportCapacity\":100,\"length\":10,\"lossPerMeter\":0.1,\"connectedWells\":2"),
                new NodeKind(NodeTypeEnum.TREATMENT_PLANT, "INTERMEDIATE",
                        "\"maxTreatmentCapacity\":100,\"contaminantWaste\":1,\"intermediateStorage\":10,\"treatmentCost\":5"),
                new NodeKind(NodeTypeEnum.PIPELINE, "INTERMEDIATE",
                        "\"maxFlowCapacity\":100,\"length\":10,\"lossPerKm\":0.1"),
                new NodeKind(NodeTypeEnum.COMPRESSING_PLANT, "INTERMEDIATE",
                        "\"maxCompressionCapacity\":100,\"processWaste\":1,\"gasConsumption\":2"),
                new NodeKind(NodeTypeEnum.GROUND_LIQUEFACTION_PLANT, "INTERMEDIATE",
                        "\"maxProcessingCapacity\":140,\"mtpaRatio\":1,\"intermediateStorage\":80,\"gasConsumption\":30"),
                new NodeKind(NodeTypeEnum.FLNG_UNIT, "INTERMEDIATE",
                        "\"maxProcessingCapacity\":140,\"mtpaRatio\":1,\"intermediateStorage\":80,\"vesselDepth\":20,"
                                + "\"hiringCost\":1000,\"shipCapacity\":3,\"gasConsumption\":30"),
                LNG_CARRIER,
                new NodeKind(NodeTypeEnum.SEAPORT_TERMINAL, "INTERMEDIATE",
                        "\"intermediateStorage\":80,\"portDepth\":15,\"shipCapacity\":3"),
                new NodeKind(NodeTypeEnum.INTERNAL_CONSUMPTION, "SINK",
                        "\"consumptionSummer\":1,\"consumptionAutumn\":2,\"consumptionWinter\":3,\"consumptionSpring\":4"),
                new NodeKind(NodeTypeEnum.INDUSTRIAL_CONSUMPTION, "SINK", "\"consumption\":5"));
    }

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCreatableType")
    void editorOfTheDerivedVerticalCreatesTheNodeAndTheServerStoresThatVertical(NodeKind kind) throws Exception {
        String name = uniqueName();
        create(fixture.versionEditorOf(kind.type().vertical()), kind, kind.type().vertical(), name)
                .andExpect(status().isOk());

        assertEquals(kind.type().vertical(), storedVertical(name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCreatableType")
    void editorOfTheDerivedVerticalDeclaringAnotherOneGets400AndNothingIsCreated(NodeKind kind) throws Exception {
        assertMismatch(create(fixture.versionEditorOf(kind.type().vertical()), kind,
                anotherVerticalThan(kind.type().vertical()), uniqueName()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCreatableType")
    void editorOfAnotherVerticalDeclaringTheCorrectOneIsJudgedByTheDerivedVertical(NodeKind kind) throws Exception {
        VerticalEnum other = anotherVerticalThan(kind.type().vertical());
        String name = uniqueName();
        ResultActions result = create(fixture.versionEditorOf(other), kind, kind.type().vertical(), name);

        if (kind.type().vertical() == VerticalEnum.INTERNAL_CONSUMPTION) {
            result.andExpect(status().isOk());
            assertEquals(VerticalEnum.INTERNAL_CONSUMPTION, storedVertical(name));
        } else {
            assertVerticalDenied(result);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCreatableType")
    void editorOfAnotherVerticalDeclaringItsOwnIsRefused(NodeKind kind) throws Exception {
        VerticalEnum other = anotherVerticalThan(kind.type().vertical());
        ResultActions result = create(fixture.versionEditorOf(other), kind, other, uniqueName());

        if (kind.type().vertical() == VerticalEnum.INTERNAL_CONSUMPTION) {
            assertMismatch(result);
        } else {
            assertVerticalDenied(result);
        }
    }

    @Test
    void extractionEditorCreatesAWellDeclaredAsExtraction() throws Exception {
        String name = uniqueName();
        create(extractionEditor(), WELL, VerticalEnum.EXTRACTION, name).andExpect(status().isOk());
        assertEquals(VerticalEnum.EXTRACTION, storedVertical(name));
    }

    @Test
    void extractionEditorCannotCreateAnLngCarrierWhateverVerticalItDeclares() throws Exception {
        User editor = extractionEditor();
        assertVerticalDenied(create(editor, LNG_CARRIER, VerticalEnum.EXTRACTION, uniqueName()));
        assertVerticalDenied(create(editor, LNG_CARRIER, VerticalEnum.INTERNAL_CONSUMPTION, uniqueName()));
        assertVerticalDenied(create(editor, LNG_CARRIER, VerticalEnum.EXPORT, uniqueName()));
        assertVerticalDenied(create(editor, LNG_CARRIER, null, uniqueName()));
    }

    @Test
    void extractionEditorDeclaringAWellAsExportGets400WithAClearMessage() throws Exception {
        create(extractionEditor(), WELL, VerticalEnum.EXPORT, uniqueName())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Node vertical does not match its type: WELL belongs to EXTRACTION, not EXPORT"));
    }

    @Test
    void bodyWithoutVerticalGetsTheVerticalOfItsType() throws Exception {
        String name = uniqueName();
        create(extractionEditor(), WELL, null, name).andExpect(status().isOk());
        assertEquals(VerticalEnum.EXTRACTION, storedVertical(name));
    }

    @Test
    void versionAdminCreatesAnyTypeButMustDeclareItsVerticalCorrectly() throws Exception {
        assertAllowedButMustDeclareCorrectly(fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN));
    }

    @Test
    void projectAdminWithoutVersionRoleCreatesAnyTypeButMustDeclareItsVerticalCorrectly() throws Exception {
        assertAllowedButMustDeclareCorrectly(fixture.projectMember(ProjectMemberType.ADMIN));
    }

    @Test
    void platformAdminCreatesAnyTypeButMustDeclareItsVerticalCorrectly() throws Exception {
        assertAllowedButMustDeclareCorrectly(fixture.user(PlatformRole.ADMIN));
    }

    @Test
    void simulatorCannotCreateNodesWhateverVerticalItDeclares() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        assertVerticalDenied(create(simulator, WELL, VerticalEnum.EXTRACTION, uniqueName()));
        assertVerticalDenied(create(simulator, WELL, VerticalEnum.EXPORT, uniqueName()));
    }

    @Test
    void extractionEditorEditsItsWellAndTheVerticalStaysExtraction() throws Exception {
        String name = uniqueName();
        edit(extractionEditor(), fixture.nodeId(), VerticalEnum.EXTRACTION, name).andExpect(status().isOk());
        assertEquals(VerticalEnum.EXTRACTION, storedVertical(name));
    }

    @Test
    void extractionEditorCannotRelabelItsWellIntoAnotherVertical() throws Exception {
        User editor = extractionEditor();
        assertMismatch(edit(editor, fixture.nodeId(), VerticalEnum.EXPORT, uniqueName()));
        assertMismatch(edit(editor, fixture.nodeId(), VerticalEnum.INTERNAL_CONSUMPTION, uniqueName()));
        assertEquals(VerticalEnum.EXTRACTION, em.find(BaseNode.class, fixture.nodeId()).getType().getVertical());
    }

    @Test
    void exportEditorCannotTouchAWell() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXPORT);
        UUID nodeId = fixture.nodeId();
        assertVerticalDenied(edit(editor, nodeId, VerticalEnum.EXTRACTION, uniqueName()));
        assertVerticalDenied(perform(editor, positionOf(nodeId)));
        assertVerticalDenied(perform(editor, basicsOf(nodeId)));
        assertVerticalDenied(perform(editor, delete(nodePath(nodeId))));
        assertTrue(fixture.version().getNodeSnapshot().stream().anyMatch(node -> node.getId().equals(nodeId)));
    }

    @Test
    void extractionEditorMovesRenamesAndDeletesAWell() throws Exception {
        User editor = extractionEditor();
        UUID nodeId = fixture.nodeId();
        perform(editor, positionOf(nodeId)).andExpect(status().isOk());
        perform(editor, basicsOf(nodeId)).andExpect(status().isOk());
        perform(editor, delete(nodePath(nodeId))).andExpect(status().isOk());
    }

    @Test
    void wellStoredWithASpoofedVerticalIsJudgedByItsType() throws Exception {
        UUID nodeId = fixture.nodeId();
        em.find(BaseNode.class, nodeId).getType().setVertical(VerticalEnum.EXPORT);
        em.flush();
        User exportEditor = fixture.versionEditorOf(VerticalEnum.EXPORT);

        assertVerticalDenied(edit(exportEditor, nodeId, VerticalEnum.EXTRACTION, uniqueName()));
        assertVerticalDenied(perform(exportEditor, positionOf(nodeId)));
        assertVerticalDenied(perform(exportEditor, basicsOf(nodeId)));
        assertVerticalDenied(perform(exportEditor, delete(nodePath(nodeId))));
    }

    @Test
    void wellStoredWithASpoofedVerticalIsShownAndRepairedAsExtraction() throws Exception {
        UUID nodeId = fixture.nodeId();
        em.find(BaseNode.class, nodeId).getType().setVertical(VerticalEnum.EXPORT);
        em.flush();
        User editor = extractionEditor();

        perform(editor, get("/version/" + fixture.version().getId() + "/node/" + nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type.vertical").value("EXTRACTION"));
        String name = uniqueName();
        edit(editor, nodeId, null, name).andExpect(status().isOk());
        assertEquals(VerticalEnum.EXTRACTION, storedVertical(name));
    }

    private void assertAllowedButMustDeclareCorrectly(User user) throws Exception {
        String name = uniqueName();
        create(user, LNG_CARRIER, VerticalEnum.EXPORT, name).andExpect(status().isOk());
        assertEquals(VerticalEnum.EXPORT, storedVertical(name));
        assertMismatch(create(user, LNG_CARRIER, VerticalEnum.EXTRACTION, uniqueName()));
    }

    private void assertMismatch(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", startsWith(MISMATCH_PREFIX)));
    }

    private void assertVerticalDenied(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(VERTICAL_DENIED));
    }

    private User extractionEditor() {
        return fixture.versionEditorOf(VerticalEnum.EXTRACTION);
    }

    private ResultActions create(User user, NodeKind kind, VerticalEnum declared, String name) throws Exception {
        long before = nodeCount();
        ResultActions result = perform(user, post("/version/" + fixture.version().getId() + "/node")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(kind, declared, name, true)));
        if (result.andReturn().getResponse().getStatus() != 200) {
            assertEquals(before, nodeCount());
        }
        return result;
    }

    private ResultActions edit(User user, UUID nodeId, VerticalEnum declared, String name) throws Exception {
        return perform(user, patch(nodePath(nodeId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(WELL, declared, name, false)));
    }

    private MockHttpServletRequestBuilder positionOf(UUID nodeId) {
        return patch(nodePath(nodeId) + "/position").contentType(MediaType.APPLICATION_JSON)
                .content("{\"graphPosition\":{\"x\":5,\"y\":6}}");
    }

    private MockHttpServletRequestBuilder basicsOf(UUID nodeId) {
        return patch(nodePath(nodeId) + "/basics").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}");
    }

    private String nodePath(UUID nodeId) {
        return "/version/" + fixture.version().getId() + "/node/" + nodeId;
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.bearer(user)));
    }

    private long nodeCount() {
        return em.createQuery("select count(n) from BaseNode n", Long.class).getSingleResult();
    }

    private VerticalEnum storedVertical(String name) {
        em.flush();
        return em.createQuery("select n from BaseNode n where n.name = :name", BaseNode.class)
                .setParameter("name", name)
                .getSingleResult()
                .getType()
                .getVertical();
    }

    private static VerticalEnum anotherVerticalThan(VerticalEnum vertical) {
        return vertical == VerticalEnum.EXTRACTION ? VerticalEnum.EXPORT : VerticalEnum.EXTRACTION;
    }

    private static String uniqueName() {
        return "Vertical " + UUID.randomUUID();
    }

    private static String body(NodeKind kind, VerticalEnum declared, String name, boolean withIdentity) {
        String vertical = declared == null ? "" : "\"vertical\":\"" + declared + "\",";
        return "{\"name\":\"" + name + "\",\"state\":\"PROPOSED\","
                + "\"startupDate\":\"2025-01-01T00:00:00Z\",\"lifespanInMonths\":120,\"upkeepCosts\":100,"
                + "\"maintenanceIntervalInDays\":30,\"operatingCosts\":50,\"wastePercentage\":0.1,"
                + "\"investmentCost\":{\"components\":[{\"name\":\"Build\",\"amount\":1000,\"costBasis\":\"FLAT\"}]},"
                + "\"graphData\":{\"graphPosition\":{\"x\":1,\"y\":2}},"
                + "\"type\":{" + vertical + "\"role\":\"" + kind.role() + "\",\"nodeType\":\"" + kind.type() + "\"},"
                + (withIdentity ? "\"identity\":\"" + UUID.randomUUID() + "\"," : "")
                + kind.fields() + "}";
    }
}
