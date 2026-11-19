package org.enerscope.economic.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.jwt.JwtService;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.user.model.User;
import org.enerscope.version.controller.AccessHttpFixture;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:access-economics;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class EconomicWriteAccessHttpTest {

    private static final String EDIT_VERSION_DENIED = "You are not allowed to edit this version";
    private static final String SIMULATE_VERSION_DENIED = "You are not allowed to simulate this version";
    private static final String PROJECT_EDIT_REQUIRED = "Project permission required: EDIT_PROJECT";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    EconomicInputCodec codec;
    @Autowired
    EconomicConfigurationRepository configurations;
    @Autowired
    EconomicDraftRepository economicDrafts;
    @Autowired
    ScenarioDraftRepository scenarioDrafts;

    private AccessHttpFixture fixture;
    private String configurationJson;
    private EconomicJob failedJob;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AccessHttpFixture(em, jwt);
        configurationJson = fixture.remapNodeIds(mapper.writeValueAsString(EconomicExample.configuration()));
        em.persist(new EconomicConfigurationEntity(fixture.version(), configurationJson));
        EconomicConfiguration configuration = mapper.readValue(configurationJson, EconomicConfiguration.class);
        failedJob = new EconomicJob(fixture.version(),
                mapper.writeValueAsString(codec.freeze(fixture.version(), configuration, 1, 42L)), 1, "42");
        ReflectionTestUtils.setField(failedJob, "status", EconomicJobStatus.FAILED);
        em.persist(failedJob);
        em.flush();
    }

    @Test
    void versionSimulatorCannotSaveEconomicOrScenarioConfiguration() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        JsonNode scenario = readScenario(simulator);

        expectDenied(saveConfiguration(simulator), EDIT_VERSION_DENIED);
        expectDenied(saveEconomicDraft(simulator), EDIT_VERSION_DENIED);
        expectDenied(saveScenario(simulator, scenarioSaveRequest(scenario)), EDIT_VERSION_DENIED);
        expectDenied(discardScenarioDraft(simulator, scenario.path("token").asText()), EDIT_VERSION_DENIED);

        em.flush();
        assertEquals(configurationJson, configurations.findByVersionId(fixture.version().getId()).orElseThrow().getConfigurationJson());
        assertFalse(economicDrafts.findByVersionId(fixture.version().getId()).isPresent());
        assertFalse(scenarioDrafts.existsByVersionId(fixture.version().getId()));
    }

    @Test
    void versionSimulatorCanEvaluateQueueAndRetry() throws Exception {
        assertSimulates(fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR));
    }

    @Test
    void versionSimulatorStillReadsConfigurationDraftScenarioAndHistory() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);

        mvc.perform(get(economics() + "/configuration").header("Authorization", fixture.bearer(simulator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currency").value("USD"));
        mvc.perform(get(economics() + "/draft").header("Authorization", fixture.bearer(simulator)))
                .andExpect(status().isOk());
        mvc.perform(get(scenarioPath()).header("Authorization", fixture.bearer(simulator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty());
        mvc.perform(get(economics() + "/evaluations").header("Authorization", fixture.bearer(simulator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get(economics() + "/evaluation-jobs").header("Authorization", fixture.bearer(simulator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(failedJob.getId().toString()));
    }

    @Test
    void versionEditorCanSaveAndSimulate() throws Exception {
        assertSavesAndSimulates(fixture.projectEditorWithVersionRoles(VersionMemberType.EDITOR));
    }

    @Test
    void versionAdminCanSaveAndSimulate() throws Exception {
        assertSavesAndSimulates(fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN));
    }

    @Test
    void projectEditorWithoutVersionRoleKeepsSavingAndSimulating() throws Exception {
        assertSavesAndSimulates(fixture.projectMember(ProjectMemberType.EDITOR));
    }

    @Test
    void outsiderGets403WithProjectPermissionMessage() throws Exception {
        assertEveryWriteDenied(fixture.organizationMember(OrganizationMemberType.MEMBER), PROJECT_EDIT_REQUIRED);
    }

    @Test
    void organizationOwnerOutsideTheProjectGets403OnEconomics() throws Exception {
        assertEveryWriteDenied(fixture.organizationMember(OrganizationMemberType.OWNER), PROJECT_EDIT_REQUIRED);
    }

    @Test
    void versionMemberWithoutRolesGets403WithSimulateMessage() throws Exception {
        User member = fixture.projectMember(ProjectMemberType.EDITOR);
        em.persist(new VersionMember(member, fixture.version()));
        em.flush();

        expectDenied(evaluate(member), SIMULATE_VERSION_DENIED);
        expectDenied(queueJob(member), SIMULATE_VERSION_DENIED);
        expectDenied(retryJob(member), SIMULATE_VERSION_DENIED);
    }

    private void assertSavesAndSimulates(User user) throws Exception {
        assertSimulates(user);
        saveConfiguration(user).andExpect(status().isOk()).andExpect(jsonPath("$.data.currency").value("USD"));
        saveScenario(user, scenarioSaveRequest(readScenario(user))).andExpect(status().isOk());
        discardScenarioDraft(user, readScenario(user).path("token").asText()).andExpect(status().isOk());
        saveEconomicDraft(user).andExpect(status().isOk()).andExpect(jsonPath("$.data.schemaVersion").value(1));
    }

    private void assertSimulates(User user) throws Exception {
        evaluate(user).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").isNotEmpty());
        queueJob(user).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED"));
        retryJob(user).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    private void assertEveryWriteDenied(User user, String message) throws Exception {
        expectDenied(saveConfiguration(user), message);
        expectDenied(saveEconomicDraft(user), message);
        expectDenied(saveScenario(user, mapper.createObjectNode().put("token", "stale")), message);
        expectDenied(discardScenarioDraft(user, "stale"), message);
        expectDenied(evaluate(user), message);
        expectDenied(queueJob(user), message);
        expectDenied(retryJob(user), message);
    }

    private void expectDenied(ResultActions result, String message) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private ResultActions saveConfiguration(User user) throws Exception {
        return mvc.perform(put(economics() + "/configuration").header("Authorization", fixture.bearer(user))
                .contentType("application/json").content(configurationJson));
    }

    private ResultActions saveEconomicDraft(User user) throws Exception {
        ObjectNode draft = mapper.createObjectNode();
        for (String field : new String[]{"startYear", "years", "currency", "wacc", "entityId", "entityName", "taxRate"}) {
            draft.put(field, "");
        }
        draft.putArray("assets");
        draft.putArray("rules");
        ObjectNode body = mapper.createObjectNode().put("schemaVersion", 1);
        body.set("draft", draft);
        return mvc.perform(put(economics() + "/draft").header("Authorization", fixture.bearer(user))
                .contentType("application/json").content(body.toString()));
    }

    private ResultActions saveScenario(User user, JsonNode request) throws Exception {
        return mvc.perform(put(scenarioPath()).header("Authorization", fixture.bearer(user))
                .contentType("application/json").content(request.toString()));
    }

    private ResultActions discardScenarioDraft(User user, String token) throws Exception {
        return mvc.perform(delete(scenarioPath() + "/draft").header("Authorization", fixture.bearer(user))
                .header("If-Match", token));
    }

    private ResultActions evaluate(User user) throws Exception {
        return mvc.perform(post(economics() + "/evaluations").header("Authorization", fixture.bearer(user)));
    }

    private ResultActions queueJob(User user) throws Exception {
        return mvc.perform(post(economics() + "/evaluation-jobs").header("Authorization", fixture.bearer(user))
                .contentType("application/json").content("{\"rounds\":1,\"seed\":\"42\"}"));
    }

    private ResultActions retryJob(User user) throws Exception {
        return mvc.perform(post(economics() + "/evaluation-jobs/" + failedJob.getId() + "/retry")
                .header("Authorization", fixture.bearer(user)));
    }

    private JsonNode readScenario(User user) throws Exception {
        String body = mvc.perform(get(scenarioPath()).header("Authorization", fixture.bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private JsonNode scenarioSaveRequest(JsonNode scenario) {
        ObjectNode request = mapper.createObjectNode();
        request.put("token", scenario.path("token").asText());
        request.put("sourceToken", scenario.path("sourceToken").asText());
        ObjectNode editor = mapper.createObjectNode().put("mode", "advanced");
        editor.set("value", scenario.path("configuration"));
        request.set("editor", editor);
        request.set("configuration", scenario.path("configuration"));
        ObjectNode operations = request.putObject("operations");
        for (JsonNode node : scenario.path("nodes")) {
            ObjectNode values = operations.putObject(node.path("id").asText());
            Iterator<Map.Entry<String, JsonNode>> fields = node.path("values").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                values.set(field.getKey(), field.getValue());
            }
        }
        return request;
    }

    private String economics() {
        return "/projects/" + fixture.project().getId() + "/versions/" + fixture.version().getId() + "/economics";
    }

    private String scenarioPath() {
        return "/projects/" + fixture.project().getId() + "/versions/" + fixture.version().getId() + "/scenario-configuration";
    }

}
