package org.enerscope.version.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.jwt.JwtService;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A project {@code EXECUTIVE} holds only {@code VIEW_PROJECT}: it reads everything
 * (project, versions, diagram, economics, results, {@code /access}) and
 * writes nothing, and it never holds version roles.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:executive-http;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProjectExecutiveHttpTest {

    private static final String WELL_BODY = "{\"name\":\"Executive attempt\",\"state\":\"PROPOSED\","
            + "\"startupDate\":\"2025-01-01T00:00:00Z\",\"lifespanInMonths\":120,\"upkeepCosts\":100,"
            + "\"maintenanceIntervalInDays\":30,\"operatingCosts\":50,\"wastePercentage\":0.1,"
            + "\"investmentCost\":{\"components\":[{\"name\":\"Build\",\"amount\":1000,\"costBasis\":\"FLAT\"}]},"
            + "\"graphData\":{\"graphPosition\":{\"x\":1,\"y\":2}},"
            + "\"type\":{\"vertical\":\"EXTRACTION\",\"role\":\"GENERATOR\",\"nodeType\":\"WELL\"},"
            + "\"identity\":\"" + UUID.randomUUID() + "\","
            + "\"maxCollectionCapacity\":1000,\"declineCurve\":{\"type\":\"CONSTANT\",\"val_a\":0.05},"
            + "\"gasRichness\":0.3,\"dtmTime\":15,\"dtmCost\":150,\"surface\":10}";

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

    private AccessHttpFixture fixture;
    private User executive;
    private EconomicJob failedJob;
    private String configurationJson;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AccessHttpFixture(em, jwt);
        executive = fixture.projectMember(ProjectMemberType.EXECUTIVE);
        configurationJson = fixture.remapNodeIds(mapper.writeValueAsString(EconomicExample.configuration()));
        var defaultScenario = org.enerscope.scenario.ScenarioFixtures.defaultScenario(em, fixture.version());
        var storedConfiguration = new EconomicConfigurationEntity(fixture.version(), configurationJson);
        storedConfiguration.assignScenario(defaultScenario);
        em.persist(storedConfiguration);
        EconomicConfiguration configuration = mapper.readValue(configurationJson, EconomicConfiguration.class);
        failedJob = new EconomicJob(fixture.version(),
                mapper.writeValueAsString(codec.freeze(fixture.version(), configuration, 1, 42L)), 1, "42");
        ReflectionTestUtils.setField(failedJob, "status", EconomicJobStatus.FAILED);
        failedJob.assignScenario(defaultScenario);
        em.persist(failedJob);
        em.flush();
    }

    // ---- reads ------------------------------------------------------------

    @Test
    void executiveReadsTheProjectItsMembersAndItsVersions() throws Exception {
        perform(executive, get("/projects")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].myRole").value("EXECUTIVE"));
        perform(executive, get("/projects/" + project() + "/members")).andExpect(status().isOk());
        perform(executive, get("/projects/" + project() + "/versions")).andExpect(status().isOk());
        perform(executive, get("/version/" + version())).andExpect(status().isOk());
    }

    @Test
    void executiveReadsTheDiagramAndAnyNode() throws Exception {
        perform(executive, get("/version/" + version() + "/diagram")).andExpect(status().isOk());
        perform(executive, get("/version/" + version() + "/node/" + fixture.nodeId())).andExpect(status().isOk());
    }

    @Test
    void executiveReadsConfigurationDraftScenarioAndResults() throws Exception {
        perform(executive, get(economics() + "/configuration")).andExpect(status().isOk());
        perform(executive, get(economics() + "/draft")).andExpect(status().isOk());
        perform(executive, get(scenarioPath())).andExpect(status().isOk());
        perform(executive, get(economics() + "/evaluations")).andExpect(status().isOk());
        perform(executive, get(economics() + "/evaluation-jobs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(failedJob.getId().toString()));
        perform(executive, get("/projects/" + project() + "/access")).andExpect(status().isOk());
    }

    @Test
    void accessSaysViewOnlyForEveryVersion() throws Exception {
        perform(executive, get("/projects/" + project() + "/access")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectRole").value("EXECUTIVE"))
                .andExpect(jsonPath("$.data.platformAdmin").value(false))
                .andExpect(jsonPath("$.data.canManageProject").value(false))
                .andExpect(jsonPath("$.data.canCreateRootVersions").value(false))
                .andExpect(jsonPath("$.data.versions[0].source").value("PROJECT_FALLBACK"))
                .andExpect(jsonPath("$.data.versions[0].effectiveRole").value("EXECUTIVE"))
                .andExpect(jsonPath("$.data.versions[0].roles.length()").value(0))
                .andExpect(jsonPath("$.data.versions[0].editableVerticals.length()").value(0))
                .andExpect(jsonPath("$.data.versions[0].canView").value(true))
                .andExpect(jsonPath("$.data.versions[0].canEdit").value(false))
                .andExpect(jsonPath("$.data.versions[0].canEditConnections").value(false))
                .andExpect(jsonPath("$.data.versions[0].canSimulate").value(false))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false))
                .andExpect(jsonPath("$.data.versions[0].canManageRoles").value(false))
                .andExpect(jsonPath("$.data.versions[0].canViewEconomicResults").value(true))
                .andExpect(jsonPath("$.data.versions[0].canViewEconomicConfiguration").value(true))
                .andExpect(jsonPath("$.data.versions[0].canEditEconomicConfiguration").value(false));
    }

    // ---- writes -----------------------------------------------------------

    @Test
    void executiveCannotCreateMoveOrEditNodes() throws Exception {
        long before = nodeCount();
        UUID node = fixture.nodeId();

        perform(executive, post("/version/" + version() + "/node").contentType(MediaType.APPLICATION_JSON)
                .content(WELL_BODY)).andExpect(status().isForbidden());
        perform(executive, patch("/version/" + version() + "/node/" + node + "/position")
                .contentType(MediaType.APPLICATION_JSON).content("{\"graphPosition\":{\"x\":5,\"y\":6}}"))
                .andExpect(status().isForbidden());
        perform(executive, patch("/version/" + version() + "/node/" + node + "/basics")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        perform(executive, patch("/version/" + version() + "/node/" + node)
                .contentType(MediaType.APPLICATION_JSON).content(WELL_BODY)).andExpect(status().isForbidden());
        perform(executive, delete("/version/" + version() + "/node/" + node)).andExpect(status().isForbidden());

        assertEquals(before, nodeCount());
    }

    @Test
    void executiveCannotCreateVersionsOrChangeOne() throws Exception {
        perform(executive, post("/projects/" + project() + "/version").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Child\",\"parentVersion\":\"" + version() + "\"}"))
                .andExpect(status().isForbidden());
        perform(executive, post("/projects/" + project() + "/version").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Root\"}")).andExpect(status().isForbidden());
        perform(executive, patch("/version/" + version()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}")).andExpect(status().isForbidden());
        perform(executive, delete("/version/" + version())).andExpect(status().isForbidden());
    }

    @Test
    void executiveCannotSaveConfigurationOrScenarioNorSimulate() throws Exception {
        perform(executive, put(economics() + "/configuration").contentType(MediaType.APPLICATION_JSON)
                .content(configurationJson)).andExpect(status().isForbidden());
        perform(executive, put(economics() + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\":1,\"draft\":{}}")).andExpect(status().isForbidden());
        perform(executive, put(scenarioPath()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"stale\"}")).andExpect(status().isForbidden());
        perform(executive, delete(scenarioPath() + "/draft").header("If-Match", "stale"))
                .andExpect(status().isForbidden());
        perform(executive, post(economics() + "/evaluations")).andExpect(status().isForbidden());
        perform(executive, post(economics() + "/evaluation-jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":1,\"seed\":\"42\"}")).andExpect(status().isForbidden());
        perform(executive, post(economics() + "/evaluation-jobs/" + failedJob.getId() + "/retry"))
                .andExpect(status().isForbidden());
    }

    @Test
    void executiveCannotManageProjectMembersOrVersionRoles() throws Exception {
        User candidate = fixture.projectMember(ProjectMemberType.EDITOR);
        UUID memberId = UUID.randomUUID();

        perform(executive, post("/projects/" + project() + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + candidate.getId() + "\",\"memberType\":\"EXECUTIVE\"}"))
                .andExpect(status().isForbidden());
        perform(executive, patch("/projects/" + project() + "/members/" + memberId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberType\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        perform(executive, delete("/projects/" + project() + "/members/" + memberId))
                .andExpect(status().isForbidden());
        perform(executive, get("/projects/" + project() + "/member-candidates")).andExpect(status().isForbidden());
        perform(executive, get("/projects/" + project() + "/version-members")).andExpect(status().isForbidden());
        perform(executive, post("/version/" + version() + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + candidate.getId() + "\",\"memberType\":\"SIMULATOR\"}"))
                .andExpect(status().isForbidden());
        perform(executive, get("/version/" + version() + "/members")).andExpect(status().isForbidden());
    }

    // ---- who may assign it ------------------------------------------------

    @Test
    void projectAdminAssignsAndRevokesExecutiveAndEditorCannot() throws Exception {
        User candidate = fixture.organizationMember(
                org.enerscope.organization.model.enums.OrganizationMemberType.MEMBER);
        String body = "{\"userId\":\"" + candidate.getId() + "\",\"memberType\":\"EXECUTIVE\"}";

        perform(fixture.projectMember(ProjectMemberType.EDITOR), post("/projects/" + project() + "/members")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        perform(fixture.projectMember(ProjectMemberType.ADMIN), post("/projects/" + project() + "/members")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberType").value("EXECUTIVE"));
        perform(fixture.user(PlatformRole.ADMIN), post("/projects/" + project() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + fixture.organizationMember(
                        org.enerscope.organization.model.enums.OrganizationMemberType.MEMBER).getId()
                        + "\",\"memberType\":\"EXECUTIVE\"}")).andExpect(status().isCreated());
    }

    // ---- version roles are refused for an Executive -----------------------

    @Test
    void assigningAVersionRoleToAnExecutiveIs400() throws Exception {
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);

        for (String role : new String[]{"SIMULATOR", "ADMIN"}) {
            perform(admin, post("/version/" + version() + "/members").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":\"" + executive.getId() + "\",\"memberType\":\"" + role + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        perform(admin, post("/version/" + version() + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + executive.getId() + "\",\"memberType\":\"EDITOR\","
                        + "\"vertical\":\"EXTRACTION\"}")).andExpect(status().isBadRequest());
        assertEquals(0, em.createQuery("select count(m) from VersionMember m where m.user.id = :u", Long.class)
                .setParameter("u", executive.getId()).getSingleResult());
    }

    @Test
    void changingAVersionRoleOfAnExecutiveIs400() throws Exception {
        // A leftover role, as if it predated the Executive type; changing it is still refused.
        VersionMember member = new VersionMember(executive, fixture.version());
        VersionMemberRole role = new VersionMemberRole("SIMULATOR", VersionMemberType.SIMULATOR,
                VersionMember.defaultPermissionsFor(VersionMemberType.SIMULATOR), null);
        member.addRole(role);
        em.persist(member);
        em.flush();

        perform(fixture.projectMember(ProjectMemberType.ADMIN), put("/version/" + version() + "/members/"
                + member.getId() + "/roles/" + role.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\":\"ADMIN\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void versionRoleErrorsKeep403Then404Then400() throws Exception {
        UUID unknown = UUID.randomUUID();
        User admin = fixture.projectMember(ProjectMemberType.ADMIN);
        String body = "{\"userId\":\"" + executive.getId() + "\",\"memberType\":\"SIMULATOR\"}";

        perform(fixture.projectMember(ProjectMemberType.EDITOR), post("/version/" + version() + "/members")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        perform(fixture.user(PlatformRole.ADMIN), post("/version/" + unknown + "/members")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        perform(admin, post("/version/" + version() + "/members").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest());
    }

    // ---- helpers ----------------------------------------------------------

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", fixture.bearer(user)));
    }

    private UUID project() {
        return fixture.project().getId();
    }

    private UUID version() {
        return fixture.version().getId();
    }

    private String economics() {
        return "/projects/" + project() + "/versions/" + version() + "/economics";
    }

    private String scenarioPath() {
        return "/projects/" + project() + "/versions/" + version() + "/scenario-configuration";
    }

    private long nodeCount() {
        return em.createQuery("select count(n) from BaseNode n", Long.class).getSingleResult();
    }
}
