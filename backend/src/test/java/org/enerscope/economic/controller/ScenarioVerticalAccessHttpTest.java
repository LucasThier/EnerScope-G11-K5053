package org.enerscope.economic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.persistence.EntityManager;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.controller.AccessHttpFixture;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:access-scenario-vertical;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ScenarioVerticalAccessHttpTest {

    private static final String VERTICAL_DENIED = "You are not allowed to edit this vertical's nodes in this version";
    private static final String EDIT_VERSION_DENIED = "You are not allowed to edit this version";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    EconomicConfigurationRepository configurations;
    @Autowired
    EconomicDraftRepository economicDrafts;
    @Autowired
    ScenarioDraftRepository scenarioDrafts;

    private AccessHttpFixture fixture;
    private String configurationJson;
    private final Map<VerticalEnum, UUID> nodeOf = new EnumMap<>(VerticalEnum.class);

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AccessHttpFixture(em, jwt);
        nodeOf.put(VerticalEnum.EXTRACTION, fixture.nodeId());
        nodeOf.put(VerticalEnum.EXPORT, fixture.retypeNode(1, NodeTypeEnum.SEAPORT_TERMINAL));
        nodeOf.put(VerticalEnum.TRANSPORTATION, fixture.addNode(NodeTypeEnum.PIPELINE));
        nodeOf.put(VerticalEnum.LIQUEFACTION, fixture.addNode(NodeTypeEnum.FLNG_UNIT));
        nodeOf.put(VerticalEnum.INTERNAL_CONSUMPTION, fixture.addNode(NodeTypeEnum.INTERNAL_CONSUMPTION));
        configurationJson = fixture.remapNodeIds(mapper.writeValueAsString(EconomicExample.configuration()));
        var defaultScenario = org.enerscope.scenario.ScenarioFixtures.defaultScenario(em, fixture.version());
        var storedConfiguration = new EconomicConfigurationEntity(fixture.version(), configurationJson);
        storedConfiguration.assignScenario(defaultScenario);
        em.persist(storedConfiguration);
        em.flush();
    }

    @ParameterizedTest
    @EnumSource(value = VerticalEnum.class, names = {"EXTRACTION", "EXPORT"})
    void editorPublishesAChangeToARuleOfItsOwnVerticalsNode(VerticalEnum vertical) throws Exception {
        User editor = fixture.versionEditorOf(vertical);

        saveScenario(editor, withConfiguration(readScenario(editor), raiseCostsOf(vertical)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());

        assertTrue(storedConfiguration().contains("99"));
        assertFalse(scenarioDrafts.existsByVersionId(versionId()));
    }

    @ParameterizedTest
    @EnumSource(value = VerticalEnum.class, names = {"EXTRACTION", "TRANSPORTATION", "LIQUEFACTION", "EXPORT"})
    void editorChangingARuleOfAnotherVerticalsNodeGets403AndNothingIsSaved(VerticalEnum vertical) throws Exception {
        User editor = fixture.versionEditorOf(vertical);
        VerticalEnum other = vertical == VerticalEnum.EXPORT ? VerticalEnum.EXTRACTION : VerticalEnum.EXPORT;

        expectDenied(saveScenario(editor, withConfiguration(readScenario(editor), raiseCostsOf(other))), VERTICAL_DENIED);

        assertNothingWritten();
    }

    @Test
    void aScenarioSaveThatWouldChangeNodeValuesIsRefusedWithAClearMessageNotSilentlyDropped() throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        saveScenario(admin, rename(readScenario(admin), nodeOf.get(VerticalEnum.EXTRACTION), "Renamed from the scenario"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("se editan en cada nodo de la versión")));

        assertNothingWritten();
    }

    @Test
    void editorResendingOtherVerticalsUnchangedSaves() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.LIQUEFACTION);

        saveScenario(editor, saveRequest(readScenario(editor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors").isEmpty());

        assertFalse(scenarioDrafts.existsByVersionId(versionId()));
    }

    @Test
    void editorChangingACostOfAnotherVerticalsNodeGets403() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        JsonNode request = withConfiguration(readScenario(editor), raiseExportCosts());

        expectDenied(saveScenario(editor, request), VERTICAL_DENIED);

        assertNothingWritten();
    }

    @Test
    void editorChangingACostOfItsOwnVerticalsNodeSaves() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXPORT);

        saveScenario(editor, withConfiguration(readScenario(editor), raiseExportCosts()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());

        assertTrue(storedConfiguration().contains("99"));
    }

    @Test
    void editorChangingOnlyGlobalDataSaves() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.TRANSPORTATION);

        saveScenario(editor, withConfiguration(readScenario(editor), tree -> tree.put("wacc", 0.12)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());

        assertEquals(0.12, mapper.readTree(storedConfiguration()).path("wacc").asDouble());
    }

    @Test
    void draftWithAnInvalidRuleOnAnotherVerticalsNodeGets403AndIsNotStored() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        JsonNode request = withConfiguration(readScenario(editor), invalidCostsOf(VerticalEnum.EXPORT));

        expectDenied(saveScenario(editor, request), VERTICAL_DENIED);

        assertNothingWritten();
    }

    @Test
    void draftWithAnInvalidRuleOnItsOwnVerticalsNodeIsStored() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        JsonNode request = withConfiguration(readScenario(editor), invalidCostsOf(VerticalEnum.EXTRACTION));

        saveScenario(editor, request).andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isNotEmpty());

        assertTrue(scenarioDrafts.existsByVersionId(versionId()));
        assertEquals(configurationJson, storedConfiguration());
    }

    @Test
    void editorOfAnyVerticalDiscardsADraftHoldingAnotherVerticalsChange() throws Exception {
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        saveScenario(admin, withConfiguration(readScenario(admin), invalidCostsOf(VerticalEnum.EXPORT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isNotEmpty());
        assertTrue(scenarioDrafts.existsByVersionId(versionId()));
        User editor = fixture.versionEditorOf(VerticalEnum.LIQUEFACTION);

        discard(editor, readScenario(editor).path("token").asText()).andExpect(status().isOk());

        assertFalse(scenarioDrafts.existsByVersionId(versionId()));
    }

    @Test
    void simulatorStillCannotSaveOrDiscard() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        JsonNode scenario = readScenario(simulator);

        expectDenied(saveScenario(simulator, rename(scenario, nodeOf.get(VerticalEnum.EXTRACTION), "Nope")), EDIT_VERSION_DENIED);
        expectDenied(discard(simulator, scenario.path("token").asText()), EDIT_VERSION_DENIED);

        assertNothingWritten();
    }

    @Test
    void versionAdminChangesNodesOfEveryVertical() throws Exception {
        assertChangesEveryVertical(fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN));
    }

    @Test
    void projectAdminWithoutVersionRoleChangesNodesOfEveryVertical() throws Exception {
        assertChangesEveryVertical(fixture.projectMember(ProjectMemberType.ADMIN));
    }

    @Test
    void platformAdminChangesNodesOfEveryVertical() throws Exception {
        assertChangesEveryVertical(fixture.user(PlatformRole.ADMIN));
    }

    @Test
    void firstSaveWithRowsOfAnotherVerticalGets403ForAnEditorAndSavesForAnAdmin() throws Exception {
        configurations.deleteAll(configurations.findAll().stream()
                .filter(e -> e.getVersion().getId().equals(versionId())).toList());
        em.flush();
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);

        expectDenied(saveScenario(editor, withConfiguration(readScenario(editor), tree -> { })), VERTICAL_DENIED);
        expectDenied(saveConfiguration(editor, configurationJson), VERTICAL_DENIED);

        assertFalse(configurations.findByVersionId(versionId()).isPresent());
        assertFalse(scenarioDrafts.existsByVersionId(versionId()));
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);
        saveScenario(admin, withConfiguration(readScenario(admin), tree -> { }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());
        assertTrue(configurations.findByVersionId(versionId()).isPresent());
    }

    @Test
    void economicConfigurationEndpointDeniesAnotherVerticalsCostAndAllowsGlobalChanges() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);

        expectDenied(saveConfiguration(editor, mutated(raiseExportCosts()).toString()), VERTICAL_DENIED);
        assertEquals(configurationJson, storedConfiguration());

        saveConfiguration(editor, mutated(tree -> tree.put("wacc", 0.12)).toString()).andExpect(status().isOk());
        assertEquals(0.12, mapper.readTree(storedConfiguration()).path("wacc").asDouble());

        saveConfiguration(fixture.versionEditorOf(VerticalEnum.EXPORT), mutated(raiseExportCosts()).toString())
                .andExpect(status().isOk());
    }

    @Test
    void economicDraftEndpointChecksTheVerticalOfEachRowsNode() throws Exception {
        User extraction = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        User export = fixture.versionEditorOf(VerticalEnum.EXPORT);
        String exportRow = economicDraft(nodeOf.get(VerticalEnum.EXPORT).toString(), "10");

        expectDenied(saveEconomicDraft(extraction, exportRow), VERTICAL_DENIED);
        assertFalse(economicDrafts.findByVersionId(versionId()).isPresent());

        String extractionRow = economicDraft(nodeOf.get(VerticalEnum.EXTRACTION).toString(), "10");
        saveEconomicDraft(export, exportRow).andExpect(status().isOk());
        saveEconomicDraft(extraction, exportRow).andExpect(status().isOk());
        expectDenied(saveEconomicDraft(extraction, economicDraft(nodeOf.get(VerticalEnum.EXPORT).toString(), "11")), VERTICAL_DENIED);
        expectDenied(saveEconomicDraft(extraction, extractionRow), VERTICAL_DENIED);
        saveEconomicDraft(export, economicDraft("", "10")).andExpect(status().isOk());
        saveEconomicDraft(extraction, extractionRow).andExpect(status().isOk());
        assertTrue(economicDrafts.findByVersionId(versionId()).orElseThrow().getDraftJson()
                .contains(nodeOf.get(VerticalEnum.EXTRACTION).toString()));
    }

    @Test
    void economicConfigurationWithAnotherVerticalsCostAndAnInvalidValueGets403Not400() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);

        expectDenied(saveConfiguration(editor, mutated(tree -> {
            raiseExportCosts().accept(tree);
            tree.put("wacc", -1);
        }).toString()), VERTICAL_DENIED);

        assertEquals(configurationJson, storedConfiguration());
    }

    @Test
    void economicConfigurationWithOnlyAnInvalidValueStillGets400() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);

        saveConfiguration(editor, mutated(tree -> tree.put("wacc", -1)).toString()).andExpect(status().isBadRequest());

        assertEquals(configurationJson, storedConfiguration());
    }

    @Test
    void economicDraftOnAnotherVerticalsNodeWithAMalformedStructureGets403Not400() throws Exception {
        User extraction = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        User export = fixture.versionEditorOf(VerticalEnum.EXPORT);
        ObjectNode malformed = (ObjectNode) mapper.readTree(
                economicDraft(nodeOf.get(VerticalEnum.EXPORT).toString(), "10"));
        ((ObjectNode) malformed.path("draft")).put("startYear", 2030);

        expectDenied(saveEconomicDraft(extraction, malformed.toString()), VERTICAL_DENIED);
        saveEconomicDraft(export, malformed.toString()).andExpect(status().isBadRequest());

        assertFalse(economicDrafts.findByVersionId(versionId()).isPresent());
    }

    @Test
    void editorCopyingTheScenarioIntoAChildVersionBecomesItsAdminAndSavesEveryVerticalThere() throws Exception {
        User editor = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        ChildCopy copy = copyScenarioIntoAChild(editor);

        copy.save().andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());

        assertTrue(configurations.findByVersionId(copy.child()).isPresent());
        assertEquals(configurationJson, storedConfiguration());
    }

    @Test
    void projectEditorWithoutVersionRoleCopiesTheScenarioIntoAChildAndSavesEveryVerticalThere() throws Exception {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        ChildCopy copy = copyScenarioIntoAChild(editor);

        copy.save().andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());

        assertTrue(configurations.findByVersionId(copy.child()).isPresent());
        assertEquals(configurationJson, storedConfiguration());
    }

    private record ChildCopy(UUID child, ResultActions save) {
    }

    private ChildCopy copyScenarioIntoAChild(User editor) throws Exception {
        JsonNode parent = readScenario(editor);
        String created = mvc.perform(post("/projects/" + fixture.project().getId() + "/version")
                        .header("Authorization", fixture.bearer(editor)).contentType("application/json")
                        .content(mapper.createObjectNode().put("name", "Copy").put("parentVersion", versionId().toString()).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID child = UUID.fromString(mapper.readTree(created).path("data").path("id").asText());
        JsonNode copy = readScenario(editor, child);
        String configuration = parent.path("configuration").toString();
        for (JsonNode node : parent.path("nodes")) {
            for (JsonNode candidate : copy.path("nodes")) {
                if (candidate.path("identity").equals(node.path("identity"))) {
                    configuration = configuration.replace(node.path("id").asText(), candidate.path("id").asText());
                }
            }
        }
        ObjectNode request = (ObjectNode) saveRequest(copy);
        request.set("configuration", mapper.readTree(configuration));
        ((ObjectNode) request.path("editor")).set("value", mapper.readTree(configuration));

        return new ChildCopy(child, saveScenario(editor, request, child));
    }

    private void assertChangesEveryVertical(User user) throws Exception {
        JsonNode request = withConfiguration(readScenario(user), tree -> {
            raiseCostsOf(VerticalEnum.EXTRACTION).accept(tree);
            raiseCostsOf(VerticalEnum.EXPORT).accept(tree);
            tree.put("wacc", 0.12);
        });

        saveScenario(user, request).andExpect(status().isOk()).andExpect(jsonPath("$.data.errors").isEmpty());

        assertTrue(storedConfiguration().contains("99"));
        assertEquals(0.12, mapper.readTree(storedConfiguration()).path("wacc").asDouble());
    }

    private void assertNothingWritten() throws Exception {
        em.flush();
        assertEquals(configurationJson, storedConfiguration());
        assertFalse(scenarioDrafts.existsByVersionId(versionId()));
        assertFalse(economicDrafts.findByVersionId(versionId()).isPresent());
    }

    private void expectDenied(ResultActions result, String message) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private Consumer<ObjectNode> raiseExportCosts() {
        return raiseCostsOf(VerticalEnum.EXPORT);
    }

    /** Sets every rule of the node profile of the given vertical's node to a unit value of 99. */
    private Consumer<ObjectNode> raiseCostsOf(VerticalEnum vertical) {
        return setCostsOf(vertical, 99);
    }

    /** A rule value the economic validator refuses (negative), so the save becomes a draft. */
    private Consumer<ObjectNode> invalidCostsOf(VerticalEnum vertical) {
        return setCostsOf(vertical, -1);
    }

    private Consumer<ObjectNode> setCostsOf(VerticalEnum vertical, int unitValue) {
        String nodeId = nodeOf.get(vertical).toString();
        return tree -> tree.withArray("nodeProfiles").forEach(profile -> {
            if (profile.path("nodeId").asText().equals(nodeId)) {
                profile.withArray("rules").forEach(rule -> ((ObjectNode) rule).put("unitValue", unitValue));
            }
        });
    }

    private ObjectNode mutated(Consumer<ObjectNode> change) throws Exception {
        ObjectNode tree = (ObjectNode) mapper.readTree(configurationJson);
        change.accept(tree);
        return tree;
    }

    private JsonNode withConfiguration(JsonNode scenario, Consumer<ObjectNode> change) throws Exception {
        ObjectNode request = (ObjectNode) saveRequest(scenario);
        ObjectNode configuration = mutated(change);
        request.set("configuration", configuration);
        ((ObjectNode) request.path("editor")).set("value", configuration);
        return request;
    }

    private JsonNode rename(JsonNode scenarioOrRequest, UUID nodeId, String name) {
        return setValue(scenarioOrRequest, nodeId, "name", name);
    }

    private JsonNode setValue(JsonNode scenarioOrRequest, UUID nodeId, String field, String value) {
        ObjectNode request = (ObjectNode) (scenarioOrRequest.has("operations") ? scenarioOrRequest : saveRequest(scenarioOrRequest));
        ((ObjectNode) request.path("operations").path(nodeId.toString())).set(field, TextNode.valueOf(value));
        return request;
    }

    private JsonNode saveRequest(JsonNode scenario) {
        ObjectNode request = mapper.createObjectNode();
        request.put("token", scenario.path("token").asText());
        request.put("sourceToken", scenario.path("sourceToken").asText());
        ObjectNode editor = mapper.createObjectNode().put("mode", "advanced");
        editor.set("value", scenario.path("configuration"));
        request.set("editor", editor);
        request.set("configuration", scenario.path("configuration"));
        ObjectNode operations = request.putObject("operations");
        for (JsonNode node : scenario.path("nodes")) {
            operations.set(node.path("id").asText(), node.path("values").deepCopy());
        }
        return request;
    }

    private String economicDraft(String nodeId, String value) {
        ObjectNode draft = mapper.createObjectNode();
        for (String field : new String[]{"startYear", "years", "currency", "wacc", "entityId", "entityName", "taxRate"}) {
            draft.put(field, "");
        }
        draft.putArray("assets");
        ArrayNode rules = draft.putArray("rules");
        rules.addObject().put("id", "rule-1").put("concept", "Cost").put("direction", "EXPENSE")
                .put("driver", nodeId.isEmpty() ? "FIXED" : "OUTPUT_VOLUME").put("nodeId", nodeId)
                .put("value", value).put("unit", "UNIT").put("factor", "1").put("from", "2031").put("to", "2032");
        ObjectNode body = mapper.createObjectNode().put("schemaVersion", 1);
        body.set("draft", draft);
        return body.toString();
    }

    private String storedConfiguration() {
        em.flush();
        return configurations.findByVersionId(versionId()).orElseThrow().getConfigurationJson();
    }

    private JsonNode readScenario(User user) throws Exception {
        return readScenario(user, versionId());
    }

    private JsonNode readScenario(User user, UUID version) throws Exception {
        String body = mvc.perform(get(scenarioPath(version)).header("Authorization", fixture.bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private ResultActions saveScenario(User user, JsonNode request) throws Exception {
        return saveScenario(user, request, versionId());
    }

    private ResultActions saveScenario(User user, JsonNode request, UUID version) throws Exception {
        return mvc.perform(put(scenarioPath(version)).header("Authorization", fixture.bearer(user))
                .contentType("application/json").content(request.toString()));
    }

    private ResultActions discard(User user, String token) throws Exception {
        return mvc.perform(delete(scenarioPath(versionId()) + "/draft").header("Authorization", fixture.bearer(user))
                .header("If-Match", token));
    }

    private ResultActions saveConfiguration(User user, String body) throws Exception {
        return mvc.perform(put(economics() + "/configuration").header("Authorization", fixture.bearer(user))
                .contentType("application/json").content(body));
    }

    private ResultActions saveEconomicDraft(User user, String body) throws Exception {
        return mvc.perform(put(economics() + "/draft").header("Authorization", fixture.bearer(user))
                .contentType("application/json").content(body));
    }

    private UUID versionId() {
        return fixture.version().getId();
    }

    private String economics() {
        return "/projects/" + fixture.project().getId() + "/versions/" + versionId() + "/economics";
    }

    private String scenarioPath(UUID version) {
        return "/projects/" + fixture.project().getId() + "/versions/" + version + "/scenario-configuration";
    }
}
