package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.enerscope.common.CsvUtil;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.organization.model.Organization;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.project.model.Project;
import org.enerscope.scenario.model.EconomicDraftEntity;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.model.ScenarioDraft;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CSV importer refuses a NODE row whose {@code identity} is already in the version, or repeated in the file, before
 * writing anything: the version keeps its nodes (same ids) and its scenarios' economic references stay valid. Also pins
 * down, with real entities, what {@code ScenarioReferenceService.remap} rewrites when an inherited node is cloned by an
 * edit: the configurations and, since the 2026-11-22 fix, the {@code scenario_draft} and legacy {@code economic_draft}
 * of every scenario of the version.
 * Runs on H2 and, through {@code VersionNodeCsvImportIdentityPostgreSqlIT}, on PostgreSQL.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:csv-identity;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class VersionNodeCsvImportIdentityTest {

    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;
    @Autowired VersionService versions;
    @Autowired EconomicService economics;
    @Autowired VersionNodeCsvImportService csvImport;
    @Autowired EconomicConfigurationRepository configurations;
    @Autowired ScenarioRepository scenarios;
    @Autowired ScenarioDraftRepository scenarioDrafts;
    @Autowired EconomicDraftRepository economicDrafts;

    UUID projectId;
    UUID versionId;
    Well well;
    UUID wellIdentity;

    @BeforeEach
    void setUp() throws Exception {
        var user = User.fromJwtClaims(UUID.randomUUID(), "csv-identity@example.com", "Csv", "Admin", PlatformRole.ADMIN);
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var nodes = new ArrayList<BaseNode>();
        var ids = new HashMap<String, String>();
        for (var original : EconomicExample.version().getNodeSnapshot()) {
            Well node = (Well) original;
            String oldId = node.getId().toString();
            ReflectionTestUtils.setField(node, "id", null);
            node.setType(new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
            node.setIdentityId(UUID.randomUUID());
            node.setGraphData(new NodeGraphData(new GraphPosition(0d, 0d), null));
            node.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
            node.setUpkeepCosts(MoneyAmount.of(2));
            node.setOperatingCosts(MoneyAmount.of(3));
            node.setDTMCost(MoneyAmount.of(4));
            node.setDeclineCurve(new ConstantValue(0));
            em.persist(node);
            nodes.add(node);
            ids.put(oldId, node.getId().toString());
        }
        well = (Well) nodes.getFirst();
        wellIdentity = well.getIdentityId();
        var organization = new Organization("Csv identity");
        em.persist(organization);
        var version = new Version("Csv identity", null, nodes, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        var project = new Project("Csv identity", "Test", organization);
        project.addVersion(version);
        em.persist(project);
        em.flush();
        projectId = project.getId();
        versionId = version.getId();
        String json = mapper.writeValueAsString(EconomicExample.configuration());
        for (var entry : ids.entrySet()) {
            json = json.replace(entry.getKey(), entry.getValue());
        }
        economics.save(projectId, versionId, mapper.readValue(json, EconomicConfiguration.class));
        em.flush();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ---- the importer ----

    @Test
    void anIdentityAlreadyInTheVersionIsRejectedAndTheVersionAndItsReferencesStayIntact() {
        var before = snapshotOf();
        String configurationBefore = baseConfigurationJson();
        assertTrue(configurationBefore.contains(well.getId().toString()), "the scenario references the well");

        var error = assertThrows(IllegalArgumentException.class, () -> importCsv(
                wellRow(UUID.randomUUID()), wellRow(wellIdentity)));

        assertEquals("Invalid CSV record 3 (NODE): identity %s already exists in this version".formatted(wellIdentity),
                error.getMessage());
        assertUntouched(before, configurationBefore);
    }

    @Test
    void anIdentityRepeatedInTheFileIsRejectedAndNothingIsWritten() {
        var before = snapshotOf();
        String configurationBefore = baseConfigurationJson();
        UUID repeated = UUID.randomUUID();

        var error = assertThrows(IllegalArgumentException.class, () -> importCsv(wellRow(repeated), wellRow(repeated)));

        assertEquals("Invalid CSV record 3 (NODE): identity %s is repeated in the file".formatted(repeated),
                error.getMessage());
        assertUntouched(before, configurationBefore);
    }

    @Test
    void aNewIdentityIsImportedAndTheExistingNodesKeepTheirIds() {
        var before = snapshotOf();
        UUID fresh = UUID.randomUUID();

        assertEquals(1, importCsv(wellRow(fresh)));
        em.flush();
        em.clear();

        var after = snapshotOf();
        assertEquals(before.size() + 1, after.size());
        assertTrue(after.keySet().containsAll(before.keySet()), "every earlier node keeps its id");
        assertTrue(after.values().contains(fresh));
    }

    // ---- ScenarioReferenceService.remap: what an edit of an inherited node rewrites ----

    @Test
    void cloningAnInheritedNodeRemapsTheScenarioConfigurations() {
        Well edited = editWellByClone();

        String configuration = baseConfigurationJson();
        assertNotEquals(well.getId(), edited.getId());
        assertTrue(configuration.contains(edited.getId().toString()));
        assertFalse(configuration.contains(well.getId().toString()));
    }

    @Test
    void cloningAnInheritedNodeRemapsTheScenarioDraftReferences() {
        var draft = new ScenarioDraft(em.find(Version.class, versionId), "{\"conversions\":[{\"nodeId\":\"" + well.getId() + "\"}]}");
        draft.assignScenario(baseScenario());
        em.persist(draft);
        em.flush();

        Well edited = editWellByClone();
        em.flush();
        em.clear();

        String draftJson = scenarioDrafts.findByScenarioId(baseScenario().getId()).orElseThrow().getDraftJson();
        assertTrue(draftJson.contains(edited.getId().toString()), "the draft should point at the cloned node");
        assertFalse(draftJson.contains(well.getId().toString()), "and no longer at the replaced one");
    }

    @Test
    void cloningAnInheritedNodeRemapsTheLegacyEconomicDraftReferences() {
        var draft = new EconomicDraftEntity(em.find(Version.class, versionId), "{\"conversions\":[{\"nodeId\":\"" + well.getId() + "\"}]}");
        draft.assignScenario(baseScenario());
        em.persist(draft);
        em.flush();

        Well edited = editWellByClone();
        em.flush();
        em.clear();

        String draftJson = economicDrafts.findByScenarioId(baseScenario().getId()).orElseThrow().getDraftJson();
        assertTrue(draftJson.contains(edited.getId().toString()), "the draft should point at the cloned node");
        assertFalse(draftJson.contains(well.getId().toString()), "and no longer at the replaced one");
    }

    @Test
    void cloningAnInheritedNodeRemapsTheDraftsOfEveryScenarioAndLeavesUnrelatedDraftsAlone() {
        var version = em.find(Version.class, versionId);
        var base = baseScenario();
        var second = scenarios.saveAndFlush(new Scenario(version, "Otro escenario", false));
        var third = scenarios.saveAndFlush(new Scenario(version, "Tercer escenario", false));
        String ref = "\"nodeId\":\"" + well.getId() + "\"";
        String otherNode = "{\"configuration\":{\"conversions\":[{\"nodeId\":\"" + UUID.randomUUID() + "\",\"factor\":1.50}]}}";

        var baseDraft = new ScenarioDraft(version, "{\"configuration\":{\"conversions\":[{" + ref + "}]}}");
        baseDraft.assignScenario(base);
        var secondDraft = new ScenarioDraft(version, "{\"editor\":{\"value\":{\"assets\":[{" + ref + "}]}}}");
        secondDraft.assignScenario(second);
        var secondLegacy = new EconomicDraftEntity(version, "{\"draft\":{\"rules\":[{" + ref + "}]}}");
        secondLegacy.assignScenario(second);
        var thirdUnrelated = new ScenarioDraft(version, otherNode);
        thirdUnrelated.assignScenario(third);
        var thirdEmpty = new EconomicDraftEntity(version, "{}");
        thirdEmpty.assignScenario(third);
        for (Object draft : List.of(baseDraft, secondDraft, secondLegacy, thirdUnrelated, thirdEmpty)) em.persist(draft);
        em.flush();

        Well edited = editWellByClone();
        em.flush();
        em.clear();

        String newId = edited.getId().toString(), oldId = well.getId().toString();
        for (String json : List.of(
                scenarioDrafts.findByScenarioId(base.getId()).orElseThrow().getDraftJson(),
                scenarioDrafts.findByScenarioId(second.getId()).orElseThrow().getDraftJson(),
                economicDrafts.findByScenarioId(second.getId()).orElseThrow().getDraftJson())) {
            assertTrue(json.contains(newId), json);
            assertFalse(json.contains(oldId), json);
        }
        assertEquals(otherNode, scenarioDrafts.findByScenarioId(third.getId()).orElseThrow().getDraftJson(),
                "a draft that does not reference the cloned node is not touched");
        assertEquals("{}", economicDrafts.findByScenarioId(third.getId()).orElseThrow().getDraftJson());
    }

    // ---- helpers ----

    /** The version's nodes as id -> identity, read fresh from the database. */
    private java.util.Map<UUID, UUID> snapshotOf() {
        em.flush();
        em.clear();
        var map = new HashMap<UUID, UUID>();
        for (BaseNode node : em.find(Version.class, versionId).getNodeSnapshot()) {
            map.put(node.getId(), node.getIdentityId());
        }
        return map;
    }

    private void assertUntouched(java.util.Map<UUID, UUID> before, String configurationBefore) {
        assertEquals(before, snapshotOf(), "same nodes, same ids, same identities");
        assertEquals(configurationBefore, baseConfigurationJson(), "the scenario configuration is byte for byte the same");
        assertTrue(baseConfigurationJson().contains(well.getId().toString()), "and still references the original well");
    }

    private Scenario baseScenario() {
        return scenarios.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(versionId).orElseThrow();
    }

    private String baseConfigurationJson() {
        em.flush();
        em.clear();
        return configurations.findByScenarioId(baseScenario().getId()).orElseThrow().getConfigurationJson();
    }

    /** Edits the well through the service: the version does not own its identity yet, so the node is cloned (new id). */
    private Well editWellByClone() {
        var dto = new WellDTO();
        dto.setName("Edited well");
        dto.setState(well.getState());
        dto.setStartupDate(well.getStartupDate());
        dto.setLifespanInMonths(well.getLifespanInMonths());
        dto.setMaintenanceIntervalInDays(well.getMaintenanceIntervalInDays());
        dto.setWastePercentage(well.getWastePercentage());
        dto.setGasRichness(well.getGasRichness());
        dto.setMaxCollectionCapacity(250f);
        dto.setUpkeepCosts(2f);
        dto.setOperatingCosts(3f);
        dto.setDTMCost(4f);
        var edited = (Well) versions.editNodeInVersion(versionId, well.getId(), dto);
        em.flush();
        return edited;
    }

    private int importCsv(List<String>... rows) {
        var all = new ArrayList<List<String>>();
        all.add(List.of("nodeKind", "name", "state", "lifespanInMonths", "upkeepCosts", "maintenanceIntervalInDays",
                "operatingCosts", "wastePercentage", "type", "investmentCost", "identity", "maxCollectionCapacity",
                "declineCurve", "gasRichness", "dtmTime", "dtmCost", "surface"));
        all.addAll(List.of(rows));
        return csvImport.importNodes(versionId, CsvUtil.write(all).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private List<String> wellRow(UUID identity) {
        return List.of("WELL", "Imported " + identity, "RUNNING", "240", "10", "30", "20", "0.1",
                "{\"vertical\":\"EXTRACTION\",\"role\":\"GENERATOR\",\"nodeType\":\"WELL\"}", "{\"components\":[]}",
                identity.toString(), "100", "{\"type\":\"CONSTANT\",\"val_a\":0.25}", "0.2", "1", "50", "50");
    }
}
