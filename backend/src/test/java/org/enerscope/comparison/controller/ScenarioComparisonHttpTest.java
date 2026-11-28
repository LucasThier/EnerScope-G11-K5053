package org.enerscope.comparison.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.jwt.JwtService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comparing scenarios over HTTP with the real security and persistence layers: two scenarios of the same version, the
 * generic 404 and the all-or-nothing 403, who may save (confirmed against the code), the deprecated version-only item, and a
 * saved comparison that keeps opening after one of its scenarios is deleted.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:scenario-comparison-http;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ScenarioComparisonHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired EconomicEvaluationRepository evaluations;

    AccessHttpFixture fixture;
    User admin;
    Scenario base;      // the default scenario, simulated
    Scenario copy;      // a second scenario of the same version, with the same configuration and its own saved simulation

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AccessHttpFixture(em, jwt);
        base = ScenarioFixtures.defaultScenario(em, fixture.version());
        var stored = new EconomicConfigurationEntity(fixture.version(), fixture.remapNodeIds(mapper.writeValueAsString(EconomicExample.configuration())));
        stored.assignScenario(base);
        em.persist(stored);
        em.flush();
        admin = fixture.projectMember(ProjectMemberType.ADMIN);

        call("post", project() + "/versions/" + fixture.version().getId() + "/economics/evaluations", admin, null).andExpect(status().isCreated());
        em.flush();
        JsonNode created = mapper.readTree(call("post", project() + "/scenarios/" + base.getId() + "/duplicate", admin, "{\"name\":\"Copia\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        copy = em.find(Scenario.class, UUID.fromString(created.path("id").asText()));
        // The copy has the same configuration and the same version, so the same simulation is a valid, up-to-date one for it.
        var original = evaluations.findByScenarioIdOrderByCreatedAtDesc(base.getId()).getFirst();
        var evaluation = new EconomicEvaluation(fixture.version(), original.getSnapshotJson());
        evaluation.assignScenario(copy);
        em.persist(evaluation);
        em.flush();
    }

    String project() {
        return "/projects/" + fixture.project().getId();
    }

    String body(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    static String scenario(Scenario s) {
        return "{\"scenarioId\":\"" + s.getId() + "\"}";
    }

    ResultActions call(String method, String path, User user, String body) throws Exception {
        var builder = switch (method) {
            case "get" -> get(path);
            case "delete" -> delete(path);
            default -> post(path);
        };
        builder.header("Authorization", fixture.bearer(user));
        if (body != null) builder.contentType("application/json").content(body);
        return mvc.perform(builder);
    }

    // ------------------------------------------------------------------ who can do what

    enum Role {
        EXECUTIVE(f -> f.projectMember(ProjectMemberType.EXECUTIVE)),
        SIMULATOR(f -> f.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR)),
        EDITOR(f -> f.versionEditorOf(VerticalEnum.EXTRACTION)),
        VERSION_ADMIN(f -> f.projectEditorWithVersionRoles(VersionMemberType.ADMIN)),
        PROJECT_ADMIN(f -> f.projectMember(ProjectMemberType.ADMIN)),
        PLATFORM_ADMIN(f -> f.user(PlatformRole.ADMIN));

        final Function<AccessHttpFixture, User> user;

        Role(Function<AccessHttpFixture, User> user) {
            this.user = user;
        }
    }

    /** Everyone with VIEW_PROJECT previews; saving needs EDIT_PROJECT, which the Simulador (a project editor) has. */
    static Stream<Arguments> saving() {
        return Stream.of(
                Arguments.of(Role.EXECUTIVE, 403), Arguments.of(Role.SIMULATOR, 201), Arguments.of(Role.EDITOR, 201),
                Arguments.of(Role.VERSION_ADMIN, 201), Arguments.of(Role.PROJECT_ADMIN, 201), Arguments.of(Role.PLATFORM_ADMIN, 201));
    }

    @ParameterizedTest(name = "{0} saves -> {1}")
    @MethodSource("saving")
    void everyRolePreviewsButOnlyThoseWhoCanEditTheProjectSave(Role role, int saveStatus) throws Exception {
        User user = role.user.apply(fixture);
        String request = body(scenario(base), scenario(copy));

        call("post", project() + "/comparisons/preview", user, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenarios.length()").value(2));
        call("post", project() + "/comparisons", user, request).andExpect(status().is(saveStatus));
    }

    // ------------------------------------------------------------------ what is compared

    @Test
    void twoScenariosOfTheSameVersionAreComparedAndNamedByTheirScenario() throws Exception {
        call("post", project() + "/comparisons/preview", admin, body(scenario(base), scenario(copy))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenarios[0].scenarioName").value("Escenario base"))
                .andExpect(jsonPath("$.data.scenarios[1].scenarioName").value("Copia"))
                .andExpect(jsonPath("$.data.scenarios[0].versionId").value(fixture.version().getId().toString()))
                .andExpect(jsonPath("$.data.scenarios[1].versionId").value(fixture.version().getId().toString()))
                .andExpect(jsonPath("$.data.scenarios[1].deleted").value(false));
    }

    @Test
    void thePickerListsEveryScenarioGroupedByVersionWithItsOwnStatus() throws Exception {
        call("get", project() + "/comparisons/scenarios", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].scenarioName").value("Escenario base"))
                .andExpect(jsonPath("$.data[0].defaultScenario").value(true))
                .andExpect(jsonPath("$.data[0].status").value("UP_TO_DATE"))
                .andExpect(jsonPath("$.data[1].scenarioName").value("Copia"))
                .andExpect(jsonPath("$.data[1].defaultScenario").value(false))
                .andExpect(jsonPath("$.data[1].versionId").value(fixture.version().getId().toString()))
                .andExpect(jsonPath("$.data[1].versionName").value(fixture.version().getName()));
    }

    @Test
    void aVersionCreatedThroughTheApiAppearsInThePickerWithItsEmptyBaseScenario() throws Exception {
        call("post", project() + "/version", admin, "{\"name\":\"Raíz nueva\"}").andExpect(status().isOk());
        String parent = fixture.version().getId().toString();
        call("post", project() + "/version", admin, "{\"name\":\"Hija nueva\",\"parentVersion\":\"" + parent + "\"}").andExpect(status().isOk());

        call("get", project() + "/comparisons/scenarios", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))   // the two scenarios of the fixture version plus one per new version
                .andExpect(jsonPath("$.data[?(@.versionName=='Raíz nueva')].scenarioName").value("Escenario base"))
                .andExpect(jsonPath("$.data[?(@.versionName=='Hija nueva')].scenarioName").value("Escenario base"))
                .andExpect(jsonPath("$.data[?(@.versionName=='Hija nueva')].status").value("NEVER_SIMULATED"))
                .andExpect(jsonPath("$.data[?(@.versionName=='Hija nueva')].defaultScenario").value(true));
    }

    @Test
    void anItemThatNamesOnlyAVersionMeansItsDefaultScenario() throws Exception {
        String legacy = "{\"versionId\":\"" + fixture.version().getId() + "\"}";

        call("post", project() + "/comparisons/preview", admin, body(legacy, scenario(copy))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenarios[0].scenarioId").value(base.getId().toString()));
    }

    // ------------------------------------------------------------------ errors

    @Test
    void anUnknownDeletedOrForeignScenarioIsTheSameGeneric404() throws Exception {
        var foreignProject = fixture.foreignProject();
        Scenario foreign = new Scenario(foreignProject.getVersions().getFirst(), "Ajeno", true);
        em.persist(foreign);
        em.flush();
        call("delete", project() + "/scenarios/" + copy.getId(), admin, null).andExpect(status().isOk());

        for (String hidden : new String[] {"{\"scenarioId\":\"" + UUID.randomUUID() + "\"}", scenario(foreign), scenario(copy)}) {
            call("post", project() + "/comparisons/preview", admin, body(scenario(base), hidden)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Scenario not found"));
        }
    }

    @Test
    void withoutAccessToTheProjectTheWholeRequestIs403WithNoPartialResult() throws Exception {
        User outsider = fixture.organizationMember(OrganizationMemberType.MEMBER);

        call("post", project() + "/comparisons/preview", outsider, body(scenario(base), scenario(copy))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
        call("post", project() + "/comparisons/preview", outsider, body(scenario(base), "{\"scenarioId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
        call("get", project() + "/comparisons/scenarios", outsider, null).andExpect(status().isForbidden());
    }

    @Test
    void tooFewTheSameTwiceAndNeverSimulatedAreBadRequests() throws Exception {
        call("post", project() + "/comparisons/preview", admin, body(scenario(base))).andExpect(status().isBadRequest());
        call("post", project() + "/comparisons/preview", admin, body(scenario(base), scenario(base))).andExpect(status().isBadRequest());
        Scenario fresh = new Scenario(fixture.version(), "Sin simular", false);
        em.persist(fresh);
        em.flush();
        call("post", project() + "/comparisons/preview", admin, body(scenario(base), scenario(fresh))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("never been simulated")));
    }

    // ------------------------------------------------------------------ saved comparisons outlive their scenarios

    @Test
    void aSavedComparisonKeepsOpeningWithItsDataAfterItsScenarioIsDeleted() throws Exception {
        String id = mapper.readTree(call("post", project() + "/comparisons", admin, body(scenario(base), scenario(copy)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
        call("get", project() + "/comparisons/" + id, admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].deleted").value(false));

        call("delete", project() + "/scenarios/" + copy.getId(), admin, null).andExpect(status().isOk());

        call("get", project() + "/comparisons/" + id, admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].deleted").value(true))
                .andExpect(jsonPath("$.data.items[1].scenarioName").value("Copia"))
                .andExpect(jsonPath("$.data.result.scenarios[1].deleted").value(true))
                .andExpect(jsonPath("$.data.result.scenarios[1].scenarioName").value("Copia"))
                .andExpect(jsonPath("$.data.result.metrics.length()").isNumber());
        call("get", project() + "/comparisons", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hasDeletedScenarios").value(true))
                .andExpect(jsonPath("$.data[0].scenarios[1]").value(fixture.version().getName() + " · Copia"));
    }

    @Test
    void aSavedComparisonKeepsOpeningAfterItsVersionIsDeactivated() throws Exception {
        String id = mapper.readTree(call("post", project() + "/comparisons", admin, body(scenario(base), scenario(copy)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
        String versionName = fixture.version().getName();

        fixture.version().deactivate();
        em.flush();

        call("get", project() + "/comparisons/" + id, admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].deleted").value(true))
                .andExpect(jsonPath("$.data.items[0].versionName").value(versionName))
                .andExpect(jsonPath("$.data.result.scenarios[0].deleted").value(true));
    }
}
