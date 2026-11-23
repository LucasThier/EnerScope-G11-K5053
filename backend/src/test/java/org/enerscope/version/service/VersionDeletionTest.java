package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.comparison.model.ScenarioComparison;
import org.enerscope.comparison.model.ScenarioComparisonItem;
import org.enerscope.comparison.repository.ScenarioComparisonRepository;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.service.ProjectService;
import org.enerscope.scenario.dto.ScenarioNameRequest;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.scenario.service.ScenarioService;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
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
 * Deleting a version: one that has simulation jobs, saved evaluations or saved comparisons — or has a version below it that
 * does — is refused with 409 before anything is touched; one without any of that is deleted with its scenarios, as before.
 * The order of errors is 403, then 404, then 409. Runs on H2 and, through {@code VersionDeletionPostgreSqlIT}, on PostgreSQL.
 */
@SpringBootTest(properties = {"economic.worker.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:version-deletion;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class VersionDeletionTest {
    static final String MESSAGE = "La versión tiene simulaciones o comparaciones guardadas y no se puede eliminar.";

    @Autowired EntityManager em;
    @Autowired ProjectService projects;
    @Autowired VersionService versions;
    @Autowired ScenarioService scenarioService;
    @Autowired ScenarioRepository scenarios;
    @Autowired VersionRepository versionRepository;
    @Autowired EconomicEvaluationRepository evaluations;
    @Autowired EconomicJobRepository jobs;
    @Autowired ScenarioComparisonRepository comparisons;

    Project project;

    void actAs(User user) {
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        authentication.setDetails(new Session("test", user, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @BeforeEach
    void setUp() {
        actAs(User.fromJwtClaims(UUID.randomUUID(), "deleter@example.com", "Version", "Deleter", PlatformRole.ADMIN));
        var organization = new Organization("Version deletion");
        em.persist(organization);
        project = new Project("Version deletion", "Test", organization);
        em.persist(project);
        em.flush();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    Version create(String name, UUID parent) {
        return projects.saveVersion(project.getId(), new VersionDTO(name, parent));
    }

    Scenario baseOf(Version version) {
        em.flush();
        return scenarios.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(version.getId()).orElseThrow();
    }

    EconomicEvaluation evaluation(Version version) {
        var evaluation = new EconomicEvaluation(version, "{}");
        evaluation.assignScenario(baseOf(version));
        em.persist(evaluation);
        return evaluation;
    }

    void detach() {
        em.flush();
        em.clear();   // as in a real request: nothing that lists the versions is managed while one is deleted
    }

    boolean exists(UUID versionId) {
        return versionRepository.findById(versionId).isPresent();
    }

    long scenariosOf(UUID versionId) {
        return scenarios.countByVersionIdAndActiveTrue(versionId);
    }

    void assertRefusedAndNothingDeleted(UUID versionId, UUID... alsoKept) {
        var error = assertThrows(VersionHasResultsException.class, () -> versions.deleteVersion(versionId));
        assertEquals(MESSAGE, error.getMessage());
        em.flush();
        em.clear();
        assertTrue(exists(versionId), "the version is still there");
        assertEquals(1, scenariosOf(versionId), "and so is its scenario");
        for (UUID kept : alsoKept) {
            assertTrue(exists(kept));
            assertEquals(1, scenariosOf(kept));
        }
    }

    @Test
    void aVersionWithASavedEvaluationIsRefusedAndNothingIsDeleted() {
        Version version = create("Con evaluación", null);
        var saved = evaluation(version);
        detach();

        assertRefusedAndNothingDeleted(version.getId());
        assertTrue(evaluations.findById(saved.getId()).isPresent(), "the evaluation is untouched");
    }

    @Test
    void aVersionWithASimulationJobIsRefusedAndNothingIsDeleted() {
        Version version = create("Con job", null);
        var job = new EconomicJob(version, "{}", 1, "1");
        job.assignScenario(baseOf(version));
        em.persist(job);
        detach();

        assertRefusedAndNothingDeleted(version.getId());
        assertEquals(1, jobs.count());
    }

    @Test
    void aVersionInASavedComparisonIsRefusedAndTheComparisonIsKept() {
        Version compared = create("Comparada", null);
        Version other = create("Otra", null);
        var otherEvaluation = evaluation(other);
        var comparison = new ScenarioComparison(project, "Guardada", null, new BigDecimal("0.05"), new BigDecimal("0.01"));
        var item = new ScenarioComparisonItem(comparison, compared, otherEvaluation, 0);
        item.assignScenario(baseOf(compared));
        comparison.addItem(item);
        comparisons.saveAndFlush(comparison);
        detach();

        // `compared` has no evaluation or job of its own: only the saved comparison holds on to it.
        assertRefusedAndNothingDeleted(compared.getId(), other.getId());
        assertEquals(1, comparisons.count());
    }

    @Test
    void anEmptyVersionIsDeletedWithAllItsScenariosAndTheRestIsUntouched() {
        Version empty = create("Vacía", null);
        Version keep = create("Queda", null);
        scenarioService.create(project.getId(), empty.getId(), new ScenarioNameRequest("Ampliación"));
        evaluation(keep);
        detach();
        assertEquals(2, scenariosOf(empty.getId()));

        versions.deleteVersion(empty.getId());
        em.flush();
        em.clear();

        assertFalse(exists(empty.getId()));
        assertEquals(0, scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(empty.getId()).size(), "its scenarios went with it");
        assertTrue(exists(keep.getId()));
        assertEquals(1, scenariosOf(keep.getId()));
        assertEquals(1, evaluations.count());
    }

    @Test
    void aSubtreeWithAChildThatHasSimulationsIsRefusedForEverything() {
        Version parent = create("Padre", null);
        Version simulated = create("Hija simulada", parent.getId());
        Version quiet = create("Hija vacía", parent.getId());
        evaluation(simulated);
        detach();

        // Deleting the (empty) parent would also delete both children: the whole delete is refused, nothing is removed.
        assertRefusedAndNothingDeleted(parent.getId(), simulated.getId(), quiet.getId());

        // The empty child, which has nothing below it and nothing of its own, can still be deleted.
        versions.deleteVersion(quiet.getId());
        em.flush();
        em.clear();
        assertFalse(exists(quiet.getId()));
        assertTrue(exists(parent.getId()));
        assertTrue(exists(simulated.getId()));
    }

    @Test
    void permissionIsCheckedBeforeTheConflictAndAnUnknownVersionStaysNotFound() {
        Version version = create("Con evaluación", null);
        evaluation(version);
        detach();

        actAs(User.fromJwtClaims(UUID.randomUUID(), "outsider@example.com", "No", "Member", PlatformRole.USER));
        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versions.deleteVersion(version.getId()),
                "403 first: a caller who cannot manage the version is not told it has results");

        actAs(User.fromJwtClaims(UUID.randomUUID(), "admin@example.com", "Plat", "Admin", PlatformRole.ADMIN));
        assertThrows(org.enerscope.common.VersionNotFoundException.class, () -> versions.deleteVersion(UUID.randomUUID()));
        assertThrows(VersionHasResultsException.class, () -> versions.deleteVersion(version.getId()));
    }

    @Test
    void aVersionWithMembersAndRolesIsDeletedTogetherWithThem() {
        Version version = create("Con miembros", null);
        User member = new User("member-" + UUID.randomUUID() + "@example.com", "Version", "Member", "hashed");
        em.persist(member);
        var membership = new org.enerscope.version.model.VersionMember(member, version);
        membership.addRole(new org.enerscope.version.model.VersionMemberRole("ADMIN",
                org.enerscope.version.model.enums.VersionMemberType.ADMIN,
                new java.util.HashSet<>(org.enerscope.version.model.VersionMember.defaultPermissionsFor(
                        org.enerscope.version.model.enums.VersionMemberType.ADMIN)), null));
        em.persist(membership);
        detach();

        versions.deleteVersion(version.getId());
        em.flush();
        em.clear();

        assertFalse(exists(version.getId()));
        assertEquals(0, em.createQuery("select count(m) from VersionMember m where m.version.id = :id", Long.class)
                .setParameter("id", version.getId()).getSingleResult());
    }

    // ---- everything a version can own without results -------------------------------------------------------------

    @Autowired org.enerscope.version.repository.VersionMemberRepository versionMembers;
    @Autowired org.enerscope.scenario.repository.ScenarioDraftRepository scenarioDrafts;
    @Autowired org.enerscope.scenario.repository.EconomicDraftRepository economicDrafts;

    static final java.util.List<String> OWNED_BY_VERSION = java.util.List.of(
            "scenario|version_id", "economic_configuration|version_id", "economic_draft|version_id", "scenario_draft|version_id",
            "version_member|version_id", "versionxnode|version_id", "versionxconnection|version_id");

    /** Rows each table holds for the given versions (plus roles, via their members). */
    java.util.Map<String, Long> ownedRows(java.util.Collection<UUID> versionIds) {
        var counts = new java.util.LinkedHashMap<String, Long>();
        for (String spec : OWNED_BY_VERSION) {
            String[] parts = spec.split("\\|");
            counts.put(parts[0], ((Number) em.createNativeQuery(
                    "select count(*) from " + parts[0] + " where " + parts[1] + " in (:ids)")
                    .setParameter("ids", versionIds).getSingleResult()).longValue());
        }
        counts.put("version_member_role", ((Number) em.createNativeQuery(
                "select count(*) from version_member_role where version_member_id in "
                        + "(select id from version_member where version_id in (:ids))")
                .setParameter("ids", versionIds).getSingleResult()).longValue());
        counts.put("version", ((Number) em.createNativeQuery("select count(*) from version where id in (:ids)")
                .setParameter("ids", versionIds).getSingleResult()).longValue());
        return counts;
    }

    org.enerscope.node.dto.WellDTO wellDto(String name) {
        var dto = new org.enerscope.node.dto.WellDTO();
        dto.setName(name);
        dto.setType(new org.enerscope.node.dto.NodeTypeDataDTO(org.enerscope.node.model.enums.VerticalEnum.EXTRACTION,
                org.enerscope.node.model.enums.StructuralRoleEnum.GENERATOR, org.enerscope.node.model.enums.NodeTypeEnum.WELL));
        dto.setState(org.enerscope.node.model.enums.NodeStateEnum.RUNNING);
        dto.setStartupDate(Instant.parse("2030-01-01T00:00:00Z"));
        dto.setLifespanInMonths(240);
        dto.setMaintenanceIntervalInDays(30);
        dto.setWastePercentage(0.02f);
        dto.setGasRichness(0.85f);
        dto.setMaxCollectionCapacity(250f);
        dto.setUpkeepCosts(2f);
        dto.setOperatingCosts(3f);
        dto.setDTMCost(4f);
        dto.setDTMTime(0);
        dto.setSurface(1200f);
        var investment = new org.enerscope.node.dto.InvestmentCostDTO();
        investment.setComponents(new java.util.ArrayList<>());
        dto.setInvestmentCost(investment);
        dto.setDeclineCurve(new org.enerscope.node.dto.ProbabilisticDistributionDTO("CONSTANT", 0f, null));
        return dto;
    }

    /** Fills a version with everything it can have before any simulation: scenarios, config, both drafts, members, nodes, connection. */
    void fillWithEverything(Version version) {
        scenarioService.create(project.getId(), version.getId(), new ScenarioNameRequest("Ampliación " + version.getName()));
        scenarioService.create(project.getId(), version.getId(), new ScenarioNameRequest("Optimista " + version.getName()));
        em.flush();
        for (Scenario scenario : scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(version.getId())) {
            var configuration = new org.enerscope.economic.model.EconomicConfigurationEntity(version, "{}");
            configuration.assignScenario(scenario);
            em.persist(configuration);
            var scenarioDraft = new org.enerscope.scenario.model.ScenarioDraft(version, "{}");
            scenarioDraft.assignScenario(scenario);
            em.persist(scenarioDraft);
            var economicDraft = new org.enerscope.scenario.model.EconomicDraftEntity(version, "{}");
            economicDraft.assignScenario(scenario);
            em.persist(economicDraft);
        }
        for (String mail : List.of("uno", "dos")) {
            User user = new User(mail + "-" + UUID.randomUUID() + "@example.com", "Version", mail, "hashed");
            em.persist(user);
            var membership = new org.enerscope.version.model.VersionMember(user, version);
            membership.addRole(new org.enerscope.version.model.VersionMemberRole("SIMULATOR",
                    org.enerscope.version.model.enums.VersionMemberType.SIMULATOR,
                    new java.util.HashSet<>(org.enerscope.version.model.VersionMember.defaultPermissionsFor(
                            org.enerscope.version.model.enums.VersionMemberType.SIMULATOR)), null));
            membership.addRole(new org.enerscope.version.model.VersionMemberRole("EDITOR",
                    org.enerscope.version.model.enums.VersionMemberType.EDITOR,
                    new java.util.HashSet<>(org.enerscope.version.model.VersionMember.defaultPermissionsFor(
                            org.enerscope.version.model.enums.VersionMemberType.EDITOR)),
                    org.enerscope.node.model.enums.VerticalEnum.EXTRACTION));
            em.persist(membership);
        }
        em.flush();
        var from = versions.addNodeToVersion(version.getId(), wellDto("Pozo A " + version.getName()));
        var to = versions.addNodeToVersion(version.getId(), wellDto("Pozo B " + version.getName()));
        var connection = new org.enerscope.node.dto.ConnectionDTO();
        connection.setFromNodeId(from.getId());
        connection.setToNodeId(to.getId());
        versions.addConnectionToVersion(version.getId(), connection);
        em.flush();
    }

    @Test
    void deletingASubtreeWithEverythingItCanOwnLeavesNoRowsBehindAndKeepsTheRest() {
        Version root = create("Raíz completa", null);
        Version child = create("Hija completa", root.getId());
        Version other = create("Otra completa", null);
        fillWithEverything(root);
        fillWithEverything(child);
        fillWithEverything(other);
        detach();

        List<UUID> subtree = List.of(root.getId(), child.getId());
        var before = ownedRows(subtree);
        var otherBefore = ownedRows(List.of(other.getId()));
        for (var entry : before.entrySet()) {
            assertTrue(entry.getValue() > 0, "the fixture must populate " + entry.getKey());
        }
        assertEquals(6, before.get("scenario_draft"), "one draft per scenario (3 each) in both versions");
        assertEquals(6, before.get("economic_draft"));

        versions.deleteVersion(root.getId());
        em.flush();
        em.clear();

        for (var entry : ownedRows(subtree).entrySet()) {
            assertEquals(0L, entry.getValue(), "no " + entry.getKey() + " row is left behind");
        }
        assertEquals(otherBefore, ownedRows(List.of(other.getId())), "an unrelated version keeps all of its rows");
        assertEquals(0L, ((Number) em.createNativeQuery(
                "select count(*) from version_member_role r where not exists (select 1 from version_member m where m.id = r.version_member_id)")
                .getSingleResult()).longValue(), "no role without a member");
        assertEquals(0L, ((Number) em.createNativeQuery(
                "select count(*) from version_member_role_permission p where not exists (select 1 from version_member_role r where r.id = p.version_member_role_id)")
                .getSingleResult()).longValue(), "no permission without a role");
        assertEquals(0L, ((Number) em.createNativeQuery(
                "select count(*) from scenario_draft d where not exists (select 1 from version v where v.id = d.version_id)")
                .getSingleResult()).longValue(), "no scenario draft without a version");
    }

    @Test
    void aSubtreeWithEverythingButAChildWithAnEvaluationIsRefusedAndNothingIsDeleted() {
        Version root = create("Raíz refugiada", null);
        Version child = create("Hija simulada", root.getId());
        fillWithEverything(root);
        fillWithEverything(child);
        evaluation(child);
        detach();

        List<UUID> subtree = List.of(root.getId(), child.getId());
        var before = ownedRows(subtree);
        var error = assertThrows(VersionHasResultsException.class, () -> versions.deleteVersion(root.getId()));
        assertEquals(MESSAGE, error.getMessage());
        em.flush();
        em.clear();

        assertEquals(before, ownedRows(subtree), "a refused delete touches no draft, member or scenario");
        assertTrue(exists(root.getId()));
        assertTrue(exists(child.getId()));
    }
}
