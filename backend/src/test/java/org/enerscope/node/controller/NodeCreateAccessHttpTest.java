package org.enerscope.node.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.controller.AccessHttpFixture;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class NodeCreateAccessHttpTest {

    private static final String ADMIN_ONLY_MESSAGE = "Only platform admins can create nodes outside a version";

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;

    private AccessHttpFixture fixture;

    record Endpoint(String path, VerticalEnum vertical, String body, Class<?> entity) {
        @Override
        public String toString() {
            return path;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
    }

    static List<Endpoint> everyEndpoint() {
        return List.of(
                node("/nodes/well", VerticalEnum.EXTRACTION, "GENERATOR", "WELL",
                        "\"maxCollectionCapacity\":1000,\"declineCurve\":{\"type\":\"CONSTANT\",\"val_a\":0.05},"
                                + "\"gasRichness\":0.3,\"dtmTime\":15,\"dtmCost\":150,\"surface\":10"),
                node("/nodes/gathering-network", VerticalEnum.EXTRACTION, "INTERMEDIATE", "GATHERING_NETWORK",
                        "\"maxTransportCapacity\":100,\"length\":10,\"lossPerMeter\":0.1,\"connectedWells\":2"),
                node("/nodes/treatment-plant", VerticalEnum.EXTRACTION, "INTERMEDIATE", "TREATMENT_PLANT",
                        "\"maxTreatmentCapacity\":100,\"contaminantWaste\":1,\"intermediateStorage\":10,"
                                + "\"treatmentCost\":5"),
                node("/nodes/pipeline", VerticalEnum.TRANSPORTATION, "INTERMEDIATE", "PIPELINE",
                        "\"maxFlowCapacity\":100,\"length\":10,\"lossPerKm\":0.1"),
                node("/nodes/pipeline-connection", VerticalEnum.TRANSPORTATION, "INTERMEDIATE", "PIPELINE_CONECTION",
                        "\"transferCapacity\":100,\"outputPriority\":1"),
                node("/nodes/compressing-plant", VerticalEnum.TRANSPORTATION, "INTERMEDIATE", "COMPRESSING_PLANT",
                        "\"maxCompressionCapacity\":100,\"processWaste\":1,\"gasConsumption\":2"),
                node("/nodes/ground-liquefaction-plant", VerticalEnum.LIQUEFACTION, "INTERMEDIATE",
                        "GROUND_LIQUEFACTION_PLANT",
                        "\"maxProcessingCapacity\":140,\"mtpaRatio\":1,\"intermediateStorage\":80,\"gasConsumption\":30"),
                node("/nodes/flng-unit", VerticalEnum.LIQUEFACTION, "INTERMEDIATE", "FLNG_UNIT",
                        "\"maxProcessingCapacity\":140,\"mtpaRatio\":1,\"intermediateStorage\":80,\"vesselDepth\":20,"
                                + "\"hiringCost\":1000,\"shipCapacity\":3,\"gasConsumption\":30"),
                node("/nodes/lng-carrier", VerticalEnum.EXPORT, "SINK", "LNG_CAMER",
                        "\"exportFrequency\":4,\"shipCapacity\":100,\"fullLoadTime\":2,\"hiringCost\":1000,"
                                + "\"timeToDestination\":10"),
                node("/nodes/seaport-terminal", VerticalEnum.EXPORT, "INTERMEDIATE", "SEAPORT_TERMINAL",
                        "\"intermediateStorage\":80,\"portDepth\":15,\"shipCapacity\":3"),
                new Endpoint("/nodes/connections", null,
                        "{\"fromNodeId\":\"" + UUID.randomUUID() + "\",\"toNodeId\":\"" + UUID.randomUUID()
                                + "\",\"identity\":\"" + UUID.randomUUID() + "\"}",
                        NodeConnection.class),
                node("/nodes/internal-consumption", VerticalEnum.INTERNAL_CONSUMPTION, "SINK", "INTERNAL_CONSUMPTION",
                        "\"consumptionSummer\":1,\"consumptionAutumn\":2,\"consumptionWinter\":3,\"consumptionSpring\":4"),
                node("/nodes/industrial-consumption", VerticalEnum.INTERNAL_CONSUMPTION, "SINK",
                        "INDUSTRIAL_CONSUMPTION", "\"consumption\":5"));
    }

    static List<Endpoint> representativeEndpoints() {
        return everyEndpoint().stream()
                .filter(endpoint -> List.of("/nodes/well", "/nodes/pipeline", "/nodes/lng-carrier")
                        .contains(endpoint.path()))
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEndpoint")
    void platformAdminCreatesTheNode(Endpoint endpoint) throws Exception {
        long before = count(endpoint);

        mvc.perform(post(endpoint.path()).header("Authorization", fixture.bearer(fixture.user(PlatformRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(before + 1, count(endpoint));
    }

    @org.junit.jupiter.api.Test
    void platformAdminDeclaringAVerticalThatDoesNotMatchTheTypeGets400AndNothingIsCreated() throws Exception {
        Endpoint endpoint = node("/nodes/lng-carrier", VerticalEnum.EXTRACTION, "SINK", "LNG_CAMER",
                "\"exportFrequency\":4,\"shipCapacity\":100,\"fullLoadTime\":2,\"hiringCost\":1000,"
                        + "\"timeToDestination\":10");
        long before = count(endpoint);

        mvc.perform(post(endpoint.path()).header("Authorization", fixture.bearer(fixture.user(PlatformRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Node vertical does not match its type: LNG_CAMER belongs to EXPORT, not EXTRACTION"));

        assertEquals(before, count(endpoint));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEndpoint")
    void projectAdminGets403AndNothingIsCreated(Endpoint endpoint) throws Exception {
        assertRefused(endpoint, fixture.projectMember(ProjectMemberType.ADMIN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeEndpoints")
    void projectEditorGets403AndNothingIsCreated(Endpoint endpoint) throws Exception {
        assertRefused(endpoint, fixture.projectMember(ProjectMemberType.EDITOR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeEndpoints")
    void versionEditorOfAnotherVerticalGets403AndNothingIsCreated(Endpoint endpoint) throws Exception {
        VerticalEnum other = endpoint.vertical() == VerticalEnum.EXTRACTION ? VerticalEnum.EXPORT : VerticalEnum.EXTRACTION;
        assertRefused(endpoint, versionEditorOf(other));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeEndpoints")
    void versionSimulatorGets403AndNothingIsCreated(Endpoint endpoint) throws Exception {
        assertRefused(endpoint, fixture.projectEditorWithVersionRoles(VersionMemberType.SIMULATOR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeEndpoints")
    void unrelatedUserGets403AndNothingIsCreated(Endpoint endpoint) throws Exception {
        assertRefused(endpoint, fixture.organizationMember(OrganizationMemberType.MEMBER));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeEndpoints")
    void organizationOwnerOutsideTheProjectGets403AndNothingIsCreated(Endpoint endpoint) throws Exception {
        assertRefused(endpoint, fixture.organizationMember(OrganizationMemberType.OWNER));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeEndpoints")
    void anonymousCallerGets401AndNothingIsCreated(Endpoint endpoint) throws Exception {
        long before = count(endpoint);

        mvc.perform(post(endpoint.path()).contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isUnauthorized());

        assertEquals(before, count(endpoint));
    }

    private void assertRefused(Endpoint endpoint, User user) throws Exception {
        long before = count(endpoint);

        mvc.perform(post(endpoint.path()).header("Authorization", fixture.bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ADMIN_ONLY_MESSAGE));

        assertEquals(before, count(endpoint));
    }

    private User versionEditorOf(VerticalEnum vertical) {
        User editor = fixture.projectMember(ProjectMemberType.EDITOR);
        VersionMember member = new VersionMember(editor, fixture.version());
        member.addRole(new VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                EnumSet.copyOf(VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR)), vertical));
        em.persist(member);
        em.flush();
        return editor;
    }

    private long count(Endpoint endpoint) {
        return em.createQuery("select count(e) from " + endpoint.entity().getSimpleName() + " e", Long.class)
                .getSingleResult();
    }

    private static Endpoint node(String path, VerticalEnum vertical, String role, String nodeType, String fields) {
        String body = "{\"name\":\"Access " + nodeType + "\",\"state\":\"PROPOSED\","
                + "\"startupDate\":\"2025-01-01T00:00:00Z\",\"lifespanInMonths\":120,\"upkeepCosts\":100,"
                + "\"maintenanceIntervalInDays\":30,\"operatingCosts\":50,\"wastePercentage\":0.1,"
                + "\"investmentCost\":{\"components\":[{\"name\":\"Build\",\"amount\":1000,\"costBasis\":\"FLAT\"}]},"
                + "\"graphData\":{\"graphPosition\":{\"x\":1,\"y\":2}},"
                + "\"type\":{\"vertical\":\"" + vertical + "\",\"role\":\"" + role + "\",\"nodeType\":\"" + nodeType + "\"},"
                + "\"identity\":\"" + UUID.randomUUID() + "\"," + fields + "}";
        return new Endpoint(path, vertical, body, BaseNode.class);
    }
}
