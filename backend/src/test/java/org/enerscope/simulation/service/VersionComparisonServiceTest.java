package org.enerscope.simulation.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.simulation.dto.ComparedVersionDTO;
import org.enerscope.simulation.dto.NodeDiffDTO;
import org.enerscope.simulation.dto.StructuralDiffDTO;
import org.enerscope.simulation.dto.VersionComparisonDTO;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.enerscope.simulation.SimulationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VersionComparisonServiceTest {

    private ProjectRepository projectRepository;
    private VersionRepository versionRepository;
    private ProjectAccessGuard accessGuard;
    private VersionComparisonService service;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        versionRepository = mock(VersionRepository.class);
        accessGuard = mock(ProjectAccessGuard.class);
        service = new VersionComparisonService(projectRepository, versionRepository, accessGuard,
                new SimulationResultMapper(), mock(AppLogger.class));
        when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(true);
    }

    /** Registers the version as one of the project's. */
    private Version inProject(Version version) {
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(projectRepository.findIdByVersionId(version.getId())).thenReturn(Optional.of(projectId));
        return version;
    }

    /** A copy of {@code node}'s identity in another branch: same identityId, new row id. */
    private static <T extends BaseNode> T sameIdentity(T copy, BaseNode original) {
        copy.setIdentityId(original.getIdentityId());
        return copy;
    }

    private static FinalResult run(int years, Instant createdAt, float delivered) {
        FinalResult result = new FinalResult(years);
        ReflectionTestUtils.setField(result, "id", UUID.randomUUID());
        result.setCreatedAt(createdAt);
        result.setRounds(10);
        ResultPerNode sink = new ResultPerNode(UUID.randomUUID(), "InternalConsumption", delivered, 0, delivered);
        for (List<ResultPerNode> list : List.of(result.getPercentile10(), result.getPercentile50(),
                result.getPercentile90())) {
            list.add(copy(sink));
        }
        return result;
    }

    private static ResultPerNode copy(ResultPerNode row) {
        return new ResultPerNode(row.getNodeID(), row.getNodeClass(), row.getTotalProduced(),
                row.getTotalDeferred(), row.getMaxPossibleProduced());
    }

    /**
     * Baseline: well → network → consumption. Branch: the same well with a
     * bigger capacity, the network soft-deleted, a new pipeline in its place.
     */
    private List<Version> baselineAndBranch() {
        Well well = well("Pozo 1", 100f);
        GatheringNetwork network = gathering("Red 1");
        InternalConsumption consumption = consumption("Consumo", 1000f);
        Version baseline = inProject(version("Base", List.of(well, network, consumption),
                List.of(connect(well, network), connect(network, consumption))));

        Well biggerWell = sameIdentity(well("Pozo 1", 250f), well);
        GatheringNetwork removedNetwork = sameIdentity(gathering("Red 1"), network);
        removedNetwork.setState(NodeStateEnum.REMOVED);
        InternalConsumption sameConsumption = sameIdentity(consumption("Consumo", 1000f), consumption);
        Pipeline pipeline = pipeline("Gasoducto nuevo");
        Version branch = inProject(version("Ampliación",
                List.of(biggerWell, removedNetwork, sameConsumption, pipeline),
                List.of(connect(biggerWell, removedNetwork), connect(removedNetwork, sameConsumption),
                        connect(biggerWell, pipeline), connect(pipeline, sameConsumption))));
        ReflectionTestUtils.setField(branch, "parentVersion", baseline);
        return List.of(baseline, branch);
    }

    @Test
    void theFirstVersionIsTheBaselineAndHasNoDiff() {
        List<Version> versions = baselineAndBranch();

        VersionComparisonDTO comparison = service.compare(projectId,
                List.of(versions.get(0).getId(), versions.get(1).getId()));

        assertEquals(versions.get(0).getId(), comparison.baselineVersionId());
        assertNull(comparison.versions().get(0).diff());
        assertEquals(versions.get(0).getId(), comparison.versions().get(1).parentVersionId());
        verify(accessGuard).assertCanViewProject(projectId);
    }

    @Test
    void theDiffMatchesNodesByIdentityAndIgnoresRemovedOnes() {
        List<Version> versions = baselineAndBranch();

        StructuralDiffDTO diff = service.compare(projectId,
                List.of(versions.get(0).getId(), versions.get(1).getId())).versions().get(1).diff();

        assertEquals(1, diff.added());
        assertEquals(1, diff.removed(), "a soft-deleted node counts as gone");
        assertEquals(1, diff.modified());
        assertEquals(1, diff.unchanged());
        NodeDiffDTO modified = diff.nodes().stream().filter(n -> n.change().equals("MODIFIED")).findFirst().orElseThrow();
        assertEquals("Pozo 1", modified.name());
        assertEquals(List.of("maxCollectionCapacity"), modified.changedFields());
        // well→network and network→consumption are gone; well→pipeline and pipeline→consumption are new.
        assertEquals(2, diff.connectionsAdded());
        assertEquals(2, diff.connectionsRemoved());
    }

    @Test
    void countsOnlyActiveNodesAndTheConnectionsBetweenThem() {
        List<Version> versions = baselineAndBranch();

        ComparedVersionDTO branch = service.compare(projectId,
                List.of(versions.get(0).getId(), versions.get(1).getId())).versions().get(1);

        assertEquals(3, branch.nodeCount());
        assertEquals(2, branch.connectionCount());
        assertEquals(Map.of("EXTRACTION", 1, "INTERNAL_CONSUMPTION", 1, "TRANSPORTATION", 1),
                branch.nodesByVertical());
    }

    @Test
    void eachVersionCarriesItsLatestRun() {
        List<Version> versions = baselineAndBranch();
        FinalResult older = run(10, Instant.parse("2026-10-01T00:00:00Z"), 100);
        FinalResult newer = run(10, Instant.parse("2026-10-03T00:00:00Z"), 300);
        versions.get(0).getFinalResults().addAll(List.of(newer, older));
        versions.get(1).getFinalResults().add(run(10, Instant.now(), 500));

        VersionComparisonDTO comparison = service.compare(projectId,
                List.of(versions.get(0).getId(), versions.get(1).getId()));

        assertEquals(newer.getId(), comparison.versions().get(0).latestSimulation().id());
        assertEquals(300, comparison.versions().get(0).latestSimulation().kpis().deliveredP50(), 0.001);
        assertNotNull(comparison.versions().get(0).verticals());
        assertTrue(comparison.warnings().isEmpty());
    }

    @Test
    void warnsWhenAVersionWasNeverSimulated() {
        List<Version> versions = baselineAndBranch();
        versions.get(0).getFinalResults().add(run(10, Instant.now(), 100));

        VersionComparisonDTO comparison = service.compare(projectId,
                List.of(versions.get(0).getId(), versions.get(1).getId()));

        assertNull(comparison.versions().get(1).latestSimulation());
        assertNull(comparison.versions().get(1).verticals());
        assertEquals(List.of(VersionComparisonService.WARNING_MISSING_SIMULATIONS), comparison.warnings());
    }

    @Test
    void warnsWhenTheLatestRunsCoverDifferentHorizons() {
        List<Version> versions = baselineAndBranch();
        versions.get(0).getFinalResults().add(run(10, Instant.now(), 100));
        versions.get(1).getFinalResults().add(run(20, Instant.now(), 100));

        VersionComparisonDTO comparison = service.compare(projectId,
                List.of(versions.get(0).getId(), versions.get(1).getId()));

        assertEquals(List.of(VersionComparisonService.WARNING_DIFFERENT_HORIZONS), comparison.warnings());
    }

    @Test
    void keepsTheRequestedOrder() {
        List<Version> versions = baselineAndBranch();

        VersionComparisonDTO comparison = service.compare(projectId,
                List.of(versions.get(1).getId(), versions.get(0).getId()));

        assertEquals(versions.get(1).getId(), comparison.baselineVersionId());
        assertEquals("Base", comparison.versions().get(1).name());
        assertNotNull(comparison.versions().get(1).diff());
    }

    @Test
    void rejectsFewerThanTwoDifferentVersions() {
        UUID only = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.compare(projectId, List.of(only)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(projectId, List.of(only, only)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(projectId, null));
        verifyNoInteractions(versionRepository);
    }

    @Test
    void rejectsMoreVersionsThanTheCap() {
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i <= VersionComparisonService.MAX_VERSIONS; i++) {
            tooMany.add(UUID.randomUUID());
        }

        assertThrows(IllegalArgumentException.class, () -> service.compare(projectId, tooMany));
    }

    @Test
    void rejectsAVersionThatBelongsToAnotherProject() {
        List<Version> versions = baselineAndBranch();
        when(projectRepository.findIdByVersionId(versions.get(1).getId())).thenReturn(Optional.of(UUID.randomUUID()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.compare(
                projectId, List.of(versions.get(0).getId(), versions.get(1).getId())));
        assertTrue(error.getMessage().contains("does not belong to this project"));
    }

    @Test
    void checksAccessToTheProjectBeforeReadingAnyVersion() {
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanViewProject(projectId);

        assertThrows(ForbiddenException.class,
                () -> service.compare(projectId, List.of(UUID.randomUUID(), UUID.randomUUID())));
        verifyNoInteractions(versionRepository);
    }

    @Test
    void rejectsAnUnknownOrInactiveProject() {
        when(projectRepository.existsByIdAndActiveTrue(projectId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.compare(projectId, List.of(UUID.randomUUID(), UUID.randomUUID())));
        verifyNoInteractions(accessGuard);
    }

    @Test
    void answersNotFoundForAnUnknownVersion() {
        when(versionRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class,
                () -> service.compare(projectId, List.of(UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void identicalVersionsHaveAnEmptyDiff() {
        Version baseline = domesticChain("Base");
        List<BaseNode> copies = new ArrayList<>();
        for (BaseNode node : baseline.getNodeSnapshot()) {
            BaseNode copy = switch (node) {
                case Well w -> sameIdentity(well(w.getName(), w.getMaxCollectionCapacity()), w);
                case GatheringNetwork g -> sameIdentity(gathering(g.getName()), g);
                case InternalConsumption c -> sameIdentity(consumption(c.getName(), c.getConsumptionSummer()), c);
                default -> throw new IllegalStateException();
            };
            copies.add(copy);
        }
        Version branch = version("Copia", copies,
                List.of(connect(copies.get(0), copies.get(1)), connect(copies.get(1), copies.get(2))));

        StructuralDiffDTO diff = service.diff(baseline, branch);

        assertEquals(0, diff.added() + diff.removed() + diff.modified());
        assertEquals(3, diff.unchanged());
        assertEquals(0, diff.connectionsAdded() + diff.connectionsRemoved());
        assertEquals(Collections.emptyList(), diff.nodes());
    }
}
