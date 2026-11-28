package org.enerscope.scenario.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.scenario.ScenarioFixtures;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.controller.AccessHttpFixture;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.node.model.enums.VerticalEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scenario API over HTTP with the real security and persistence layers: the role matrix for every action, the
 * order of the answers (403, 404, 403 on the version, 400, 409), isolation between projects and between scenarios, and
 * the old version endpoints keeping to the default scenario.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:scenario-api-http;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ScenarioApiHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;

    AccessHttpFixture fixture;
    Scenario base;      // the default scenario, with a published configuration
    Scenario second;    // an empty second scenario

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AccessHttpFixture(em, jwt);
        String configuration = fixture.remapNodeIds(mapper.writeValueAsString(EconomicExample.configuration()));
        base = ScenarioFixtures.defaultScenario(em, fixture.version());
        var stored = new EconomicConfigurationEntity(fixture.version(), configuration);
        stored.assignScenario(base);
        em.persist(stored);
        second = new Scenario(fixture.version(), "Ampliación", false);
        em.persist(second);
        em.flush();
    }

    // ------------------------------------------------------------------ the role matrix

    enum Role {
        EXECUTIVE(f -> f.projectMember(ProjectMemberType.EXECUTIVE)),
        SIMULATOR(f -> f.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR)),
        EDITOR_OF_EXTRACTION(f -> f.versionEditorOf(VerticalEnum.EXTRACTION)),
        EDITOR_OF_EXPORT(f -> f.versionEditorOf(VerticalEnum.EXPORT)),
        VERSION_ADMIN(f -> f.projectEditorWithVersionRoles(VersionMemberType.ADMIN)),
        PROJECT_ADMIN(f -> f.projectMember(ProjectMemberType.ADMIN)),
        PLATFORM_ADMIN(f -> f.user(PlatformRole.ADMIN));

        final Function<AccessHttpFixture, User> user;

        Role(Function<AccessHttpFixture, User> user) {
            this.user = user;
        }
    }

    enum Action { LIST, READ, CREATE, RENAME, DUPLICATE, DELETE, SIMULATE, SAVE_CONFIGURATION, EVALUATIONS }

    /** Expected status per role, in the order of {@link Action}. */
    static Stream<Arguments> matrix() {
        int[] reader = {200, 200, 403, 403, 403, 403, 403, 403, 200};
        int[] simulator = {200, 200, 403, 403, 403, 403, 202, 403, 200};
        int[] editor = {200, 200, 201, 200, 201, 403, 202, 200, 200};
        int[] administrator = {200, 200, 201, 200, 201, 200, 202, 200, 200};
        java.util.Map<Role, int[]> expected = new java.util.EnumMap<>(Role.class);
        expected.put(Role.EXECUTIVE, reader);
        expected.put(Role.SIMULATOR, simulator);
        expected.put(Role.EDITOR_OF_EXTRACTION, editor);
        expected.put(Role.EDITOR_OF_EXPORT, editor);
        expected.put(Role.VERSION_ADMIN, administrator);
        expected.put(Role.PROJECT_ADMIN, administrator);
        expected.put(Role.PLATFORM_ADMIN, administrator);
        return expected.entrySet().stream().flatMap(entry -> Stream.of(Action.values())
                .map(action -> Arguments.of(entry.getKey(), action, entry.getValue()[action.ordinal()])));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("matrix")
    void everyRoleGetsTheExpectedAnswerForEveryAction(Role role, Action action, int expected) throws Exception {
        User user = role.user.apply(fixture);

        ResultActions result = perform(action, user);

        result.andExpect(status().is(expected));
        if (expected == 403) result.andExpect(jsonPath("$.success").value(false));
    }

    private ResultActions perform(Action action, User user) throws Exception {
        return switch (action) {
            case LIST -> call(HttpMethod.GET, versionScenarios(), user, null);
            case READ -> call(HttpMethod.GET, scenario(base), user, null);
            case CREATE -> call(HttpMethod.POST, versionScenarios(), user, name("Nuevo"));
            case RENAME -> call(HttpMethod.PATCH, scenario(second), user, name("Renombrado"));
            case DUPLICATE -> call(HttpMethod.POST, scenario(second) + "/duplicate", user, name("Copia"));
            case DELETE -> call(HttpMethod.DELETE, scenario(second), user, null);
            case SIMULATE -> call(HttpMethod.POST, scenario(base) + "/simulations", user, null);
            case SAVE_CONFIGURATION -> {
                JsonNode state = read(scenario(base) + "/configuration");
                ObjectNode request = saveRequest(state);
                ((ObjectNode) request.path("configuration")).put("wacc", 0.12);
                ((ObjectNode) request.path("editor").path("value")).put("wacc", 0.12);
                yield call(HttpMethod.PUT, scenario(base) + "/configuration", user, request.toString());
            }
            case EVALUATIONS -> call(HttpMethod.GET, scenario(base) + "/evaluations", user, null);
        };
    }

    // ------------------------------------------------------------------ vertical scope on the configuration

    @Test
    void anEditorSavesRulesOfItsOwnVerticalAndGets403ForAnotherVerticalsRules() throws Exception {
        fixture.retypeNode(1, NodeTypeEnum.SEAPORT_TERMINAL);
        User extraction = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        User export = fixture.versionEditorOf(VerticalEnum.EXPORT);
        UUID exportNode = null;
        for (JsonNode profile : read(scenario(base) + "/configuration").path("configuration").path("nodeProfiles")) {
            if (!profile.path("nodeId").asText().equals(fixture.nodeId().toString())) exportNode = UUID.fromString(profile.path("nodeId").asText());
        }
        assertNotNull(exportNode);

        String ownRules = withProfileValue(exportNode, 99);
        call(HttpMethod.PUT, scenario(base) + "/configuration", extraction, ownRules).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to edit this vertical's nodes in this version"));
        call(HttpMethod.PUT, scenario(base) + "/configuration", export, ownRules).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors").isEmpty());
    }

    @Test
    void duplicatingAConfiguredScenarioNeedsTheRightToEditEveryVerticalItsRulesTouch() throws Exception {
        fixture.retypeNode(1, NodeTypeEnum.SEAPORT_TERMINAL);
        User extraction = fixture.versionEditorOf(VerticalEnum.EXTRACTION);
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        call(HttpMethod.POST, scenario(base) + "/duplicate", extraction, name("Copia")).andExpect(status().isForbidden());
        call(HttpMethod.POST, scenario(base) + "/duplicate", admin, name("Copia")).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ order of the answers

    @Test
    void aNonMemberGets403BeforeAnythingIsLookedUpEvenForAScenarioThatDoesNotExist() throws Exception {
        User outsider = fixture.organizationMember(OrganizationMemberType.MEMBER);

        call(HttpMethod.GET, scenario(base), outsider, null).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/projects/" + fixture.project().getId() + "/scenarios/" + UUID.randomUUID(), outsider, null)
                .andExpect(status().isForbidden());
        call(HttpMethod.DELETE, "/projects/" + fixture.project().getId() + "/scenarios/" + UUID.randomUUID(), outsider, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownDeletedOrForeignScenarioIsTheSameGeneric404() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        Scenario foreign = foreignScenario();
        var deleted = call(HttpMethod.POST, versionScenarios(), admin, name("Temporal")).andExpect(status().isCreated());
        UUID deletedId = UUID.fromString(mapper.readTree(deleted.andReturn().getResponse().getContentAsString()).path("data").path("id").asText());
        call(HttpMethod.DELETE, "/projects/" + fixture.project().getId() + "/scenarios/" + deletedId, admin, null).andExpect(status().isOk());

        for (String path : new String[] {
                "/projects/" + fixture.project().getId() + "/scenarios/" + UUID.randomUUID(),
                "/projects/" + fixture.project().getId() + "/scenarios/" + foreign.getId(),
                "/projects/" + fixture.project().getId() + "/scenarios/" + deletedId}) {
            call(HttpMethod.GET, path, admin, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Scenario not found"));
            call(HttpMethod.PATCH, path, admin, name("x")).andExpect(status().isNotFound());
            call(HttpMethod.DELETE, path, admin, null).andExpect(status().isNotFound());
            call(HttpMethod.POST, path + "/duplicate", admin, name("x")).andExpect(status().isNotFound());
            call(HttpMethod.GET, path + "/configuration", admin, null).andExpect(status().isNotFound());
            call(HttpMethod.PUT, path + "/configuration", admin, "{}").andExpect(status().isNotFound());
            call(HttpMethod.POST, path + "/simulations", admin, null).andExpect(status().isNotFound());
            call(HttpMethod.GET, path + "/evaluations", admin, null).andExpect(status().isNotFound());
        }
    }

    @Test
    void aMemberOfAnotherProjectCannotReachThisProjectsScenarios() throws Exception {
        Scenario foreign = foreignScenario();
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);

        // The foreign project's id in the URL: the caller is not a member there, so 403 for everything.
        UUID foreignProject = projectOf(foreign);
        call(HttpMethod.GET, "/projects/" + foreignProject + "/scenarios/" + foreign.getId(), admin, null).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/projects/" + foreignProject + "/versions/" + foreign.getVersion().getId() + "/scenarios", admin, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void anInactiveVersionHidesItsScenarios() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        fixture.version().deactivate();
        em.flush();

        call(HttpMethod.GET, versionScenarios(), admin, null).andExpect(status().isNotFound());
        call(HttpMethod.GET, scenario(base), admin, null).andExpect(status().isNotFound());
        call(HttpMethod.POST, versionScenarios(), admin, name("Nuevo")).andExpect(status().isNotFound());
    }

    @Test
    void theVersionCapabilityIs403BeforeTheNameIsValidatedAndTheNameIs400BeforeAConflict() throws Exception {
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        User executive = fixture.projectMember(ProjectMemberType.EXECUTIVE);
        User admin = fixture.projectEditorWithVersionRoles(VersionMemberType.ADMIN);

        call(HttpMethod.PATCH, scenario(second), executive, name("")).andExpect(status().isForbidden());
        call(HttpMethod.PATCH, scenario(second), simulator, name("")).andExpect(status().isForbidden());
        call(HttpMethod.PATCH, scenario(second), admin, name("")).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, scenario(second), admin, null).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, scenario(second), admin, name("escenario BASE")).andExpect(status().isConflict());
    }

    @Test
    void repeatedNamesAndTheLimitAnswer409WithAClearMessage() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);

        call(HttpMethod.POST, versionScenarios(), admin, name("AMPLIACIÓN")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ya existe un escenario")));
        for (int i = 3; i <= 20; i++) call(HttpMethod.POST, versionScenarios(), admin, name("Escenario " + i)).andExpect(status().isCreated());
        call(HttpMethod.POST, versionScenarios(), admin, name("Demasiados")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("20")));
        call(HttpMethod.POST, scenario(second) + "/duplicate", admin, name("Otra copia")).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ what the endpoints return and do

    @Test
    void createReturnsTheScenarioAndNeverCreatesAVersion() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        long versionsBefore = fixture.project().getVersions().size();

        call(HttpMethod.POST, versionScenarios(), admin, name("Conservador")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Conservador"))
                .andExpect(jsonPath("$.data.versionId").value(fixture.version().getId().toString()))
                .andExpect(jsonPath("$.data.defaultScenario").value(false))
                .andExpect(jsonPath("$.data.configured").value(false));

        em.flush();
        em.refresh(fixture.project());
        assertEquals(versionsBefore, fixture.project().getVersions().size());
        call(HttpMethod.GET, versionScenarios(), admin, null).andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Escenario base")).andExpect(jsonPath("$.data[0].defaultScenario").value(true));
    }

    @Test
    void duplicatingGivesTheCopyTheSameConfigurationAndTheSourceStaysTheSame() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        JsonNode source = read(scenario(base) + "/configuration").path("configuration");

        JsonNode copy = mapper.readTree(call(HttpMethod.POST, scenario(base) + "/duplicate", admin, name("Copia del base"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.configured").value(true))
                .andReturn().getResponse().getContentAsString()).path("data");

        assertEquals(source, read("/projects/" + fixture.project().getId() + "/scenarios/" + copy.path("id").asText() + "/configuration")
                .path("configuration"));
        assertEquals(source, read(scenario(base) + "/configuration").path("configuration"));
    }

    @Test
    void theLastScenarioCannotBeDeletedAndTheDefaultIsPromotedWhenItIsDeleted() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);

        call(HttpMethod.DELETE, scenario(base), admin, null).andExpect(status().isOk());
        call(HttpMethod.GET, versionScenarios(), admin, null).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.data[0].defaultScenario").value(true));
        call(HttpMethod.DELETE, scenario(second), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("único escenario")));
    }

    @Test
    void theOldVersionEndpointsFollowTheDefaultScenarioAndTheNewOnesKeepToTheirOwn() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        String oldConfiguration = "/projects/" + fixture.project().getId() + "/versions/" + fixture.version().getId() + "/economics/configuration";
        JsonNode copy = mapper.readTree(call(HttpMethod.POST, scenario(base) + "/duplicate", admin, name("Copia"))
                .andReturn().getResponse().getContentAsString()).path("data");
        String copyPath = "/projects/" + fixture.project().getId() + "/scenarios/" + copy.path("id").asText();
        ObjectNode request = saveRequest(read(copyPath + "/configuration"));
        ((ObjectNode) request.path("configuration")).put("wacc", 0.33);
        ((ObjectNode) request.path("editor").path("value")).put("wacc", 0.33);
        call(HttpMethod.PUT, copyPath + "/configuration", admin, request.toString()).andExpect(status().isOk());

        double defaultWacc = read(oldConfiguration).path("wacc").asDouble();
        assertNotEquals(0.33, defaultWacc, "saving the copy must not touch the default scenario");
        // The oldest remaining scenario is promoted, so remove the older empty one first: the copy is then the oldest.
        call(HttpMethod.DELETE, scenario(second), admin, null).andExpect(status().isOk());
        call(HttpMethod.DELETE, scenario(base), admin, null).andExpect(status().isOk());
        assertEquals(0.33, read(oldConfiguration).path("wacc").asDouble(), "the old endpoint now reads the promoted scenario");
    }

    @Test
    void aSimulationBelongsToItsScenarioAndAPendingDraftBlocksIt() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        String job = mapper.readTree(call(HttpMethod.POST, scenario(base) + "/simulations", admin, null).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        call(HttpMethod.GET, scenario(base) + "/simulations", admin, null).andExpect(jsonPath("$.data.length()").value(1));
        call(HttpMethod.GET, scenario(base) + "/simulations/" + job, admin, null).andExpect(status().isOk());
        call(HttpMethod.GET, scenario(second) + "/simulations", admin, null).andExpect(jsonPath("$.data.length()").value(0));
        call(HttpMethod.GET, scenario(second) + "/simulations/" + job, admin, null).andExpect(status().isNotFound());

        // A draft on the second scenario blocks its own simulation only.
        ObjectNode draft = saveRequest(read(scenario(second) + "/configuration"));
        draft.putNull("configuration");
        call(HttpMethod.PUT, scenario(second) + "/configuration", admin, draft.toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors").isNotEmpty());
        call(HttpMethod.POST, scenario(second) + "/simulations", admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void anEvaluationOfAnotherScenarioIsNotFoundUnderThisOne() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        var evaluation = new EconomicEvaluation(fixture.version(), "{}");
        evaluation.assignScenario(base);
        em.persist(evaluation);
        em.flush();

        call(HttpMethod.GET, scenario(second) + "/evaluations", admin, null).andExpect(jsonPath("$.data.length()").value(0));
        call(HttpMethod.GET, scenario(second) + "/evaluations/" + evaluation.getId(), admin, null).andExpect(status().isNotFound());
        call(HttpMethod.GET, scenario(second) + "/evaluations/" + evaluation.getId() + "/rounds", admin, null).andExpect(status().isNotFound());
    }

    @Test
    void aScenarioSaveThatWouldChangeNodeValuesIsABadRequestWithAClearMessage() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        ObjectNode request = saveRequest(read(scenario(base) + "/configuration"));
        ((ObjectNode) request.path("operations").elements().next()).put("name", "Renamed from the scenario");

        call(HttpMethod.PUT, scenario(base) + "/configuration", admin, request.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("se editan en cada nodo de la versión")));
    }

    @Test
    void deletingAVersionThatHasSavedResultsIs409WithAClearMessageAndNothingIsDeleted() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        var evaluation = new EconomicEvaluation(fixture.version(), "{}");
        evaluation.assignScenario(base);
        em.persist(evaluation);
        em.flush();

        call(HttpMethod.DELETE, "/version/" + fixture.version().getId(), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("La versión tiene simulaciones o comparaciones guardadas y no se puede eliminar."));
        call(HttpMethod.GET, versionScenarios(), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        // Without permission the answer is 403, not a hint that results exist.
        User simulator = fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR);
        call(HttpMethod.DELETE, "/version/" + fixture.version().getId(), simulator, null).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private String versionScenarios() {
        return "/projects/" + fixture.project().getId() + "/versions/" + fixture.version().getId() + "/scenarios";
    }

    private String scenario(Scenario scenario) {
        return "/projects/" + fixture.project().getId() + "/scenarios/" + scenario.getId();
    }

    private static String name(String value) {
        return value == null ? null : "{\"name\":\"" + value + "\"}";
    }

    private Scenario foreignScenario() {
        var foreign = fixture.foreignProject();
        var version = foreign.getVersions().getFirst();
        Scenario scenario = new Scenario(version, "Ajeno", true);
        em.persist(scenario);
        em.flush();
        return scenario;
    }

    private UUID projectOf(Scenario scenario) {
        return em.createQuery("select p.id from Project p join p.versions v where v.id = :v", UUID.class)
                .setParameter("v", scenario.getVersion().getId()).getSingleResult();
    }

    private ResultActions call(HttpMethod method, String path, User user, String body) throws Exception {
        MockHttpServletRequestBuilder builder = switch (method.name()) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method.name());
        };
        builder.header("Authorization", fixture.bearer(user));
        if (body != null) builder.contentType("application/json").content(body);
        return mvc.perform(builder);
    }

    /** A read as a platform administrator (the matrix cases act as each role; setup reads are not under test). */
    private JsonNode read(String path) throws Exception {
        User reader = fixture.user(PlatformRole.ADMIN);
        String body = call(HttpMethod.GET, path, reader, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private ObjectNode saveRequest(JsonNode state) {
        ObjectNode request = mapper.createObjectNode();
        request.put("token", state.path("token").asText());
        request.put("sourceToken", state.path("sourceToken").asText());
        ObjectNode editor = mapper.createObjectNode().put("mode", "advanced");
        editor.set("value", state.path("configuration").isMissingNode() || state.path("configuration").isNull()
                ? emptyAdvancedValue() : state.path("configuration").deepCopy());
        request.set("editor", editor);
        request.set("configuration", state.path("configuration").deepCopy());
        ObjectNode operations = request.putObject("operations");
        for (JsonNode node : state.path("nodes")) operations.set(node.path("id").asText(), node.path("values").deepCopy());
        return request;
    }

    private ObjectNode emptyAdvancedValue() {
        ObjectNode value = mapper.createObjectNode();
        for (String field : new String[] {"taxEntities", "boundary", "nodeProfiles", "adjustments", "contracts", "assets", "taxTreatments", "conversions"}) {
            value.putArray(field);
        }
        return value;
    }

    /** The default scenario's configuration with every rule of the given node's profile set to {@code unitValue}. */
    private String withProfileValue(UUID nodeId, int unitValue) throws Exception {
        ObjectNode request = saveRequest(read(scenario(base) + "/configuration"));
        for (JsonNode profile : request.path("configuration").path("nodeProfiles")) {
            if (profile.path("nodeId").asText().equals(nodeId.toString())) {
                profile.withArray("rules").forEach(rule -> ((ObjectNode) rule).put("unitValue", unitValue));
            }
        }
        ((ObjectNode) request.path("editor")).set("value", request.path("configuration").deepCopy());
        return request.toString();
    }
}
