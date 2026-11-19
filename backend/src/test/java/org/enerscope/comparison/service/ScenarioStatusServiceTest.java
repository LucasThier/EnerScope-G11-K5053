package org.enerscope.comparison.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.comparison.model.enums.ScenarioStatus;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.project.model.Project;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.Readiness;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.version.dto.ProjectAccessDTO;
import org.enerscope.version.dto.VersionAccessDTO;
import org.enerscope.version.service.VersionAccessService;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.enerscope.economic.service.EconomicExample.MAPPER;
import static org.enerscope.economic.service.EconomicExample.configuration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScenarioStatusServiceTest {

    final EconomicService economics = mock(EconomicService.class);
    final ScenarioConfigurationService scenarios = mock(ScenarioConfigurationService.class);
    final VersionAccessService access = mock(VersionAccessService.class);
    final EconomicInputCodec codec = new EconomicInputCodec(MAPPER);
    final EconomicEvaluationRepository evaluations = mock(EconomicEvaluationRepository.class);
    final EconomicJobRepository jobs = mock(EconomicJobRepository.class);
    final ScenarioStatusService service = new ScenarioStatusService(economics, scenarios, access, codec, evaluations, jobs, MAPPER,
            mock(AppLogger.class));

    /** What a simulation of {@code version} with {@code configuration} freezes, as the economic module stores it. */
    EvaluationDTO.Snapshot simulated(Version version, EconomicConfiguration configuration) throws Exception {
        var input = codec.freeze(version, configuration, 1, 42);
        var snapshot = new EvaluationDTO.Snapshot(2, configuration, codec.displayNodes(input), input.connections(), List.of(), null);
        // Through JSON, like a snapshot read back from the database.
        return MAPPER.readValue(MAPPER.writeValueAsString(snapshot), EvaluationDTO.Snapshot.class);
    }

    Well firstWell(Version version) {
        return (Well) version.getNodeSnapshot().getFirst();
    }

    @Test
    void scenarioUnchangedSinceItsSimulationIsUpToDate() throws Exception {
        var version = EconomicExample.version();
        var snapshot = simulated(version, configuration());
        when(economics.configuration(version.getId())).thenReturn(configuration());

        assertTrue(service.isUpToDate(EconomicExample.version(), snapshot));
    }

    @Test
    void changingTheEconomicConfigurationMakesItOutdated() throws Exception {
        var version = EconomicExample.version();
        var snapshot = simulated(version, configuration());
        when(economics.configuration(version.getId())).thenReturn(EconomicExample.mutate(tree -> tree.put("wacc", 0.12)));

        assertFalse(service.isUpToDate(version, snapshot));
    }

    @Test
    void changingAPhysicalNodeMakesItOutdated() throws Exception {
        var snapshot = simulated(EconomicExample.version(), configuration());
        var changed = EconomicExample.version();
        firstWell(changed).setMaxCollectionCapacity(999);
        when(economics.configuration(changed.getId())).thenReturn(configuration());

        assertFalse(service.isUpToDate(changed, snapshot));
    }

    @Test
    void auditTimestampsDoNotCountAsAChange() throws Exception {
        var snapshot = simulated(EconomicExample.version(), configuration());
        var touched = EconomicExample.version();
        ReflectionTestUtils.setField(firstWell(touched), "lastModified", Instant.parse("2030-01-01T00:00:00Z"));
        when(economics.configuration(touched.getId())).thenReturn(configuration());

        assertTrue(service.isUpToDate(touched, snapshot));
    }

    @Test
    void scenarioWithoutConfigurationOrSnapshotDataIsNotUpToDate() throws Exception {
        var version = EconomicExample.version();
        var snapshot = simulated(version, configuration());
        when(economics.configuration(version.getId())).thenThrow(new EntityNotFoundException("Economic configuration not found"));

        assertFalse(service.isUpToDate(version, snapshot));
        assertFalse(service.isUpToDate(version, null));
        assertFalse(service.isUpToDate(version, new EvaluationDTO.Snapshot(1, null, null, null, null, null)));
    }

    @Test
    void listReportsEachScenarioStatusReadinessAndWhetherTheCallerCanSimulate() throws Exception {
        var project = new Project("Proyecto", "", null);
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        var simulated = EconomicExample.version();
        var never = version("Sin simular");
        var running = version("Simulando");
        List.of(simulated, never, running).forEach(project::addVersion);
        when(economics.authorizeProject(project.getId(), org.enerscope.project.model.enums.ProjectMemberPermission.VIEW_PROJECT))
                .thenReturn(project);
        when(scenarios.readiness(project.getId())).thenReturn(List.of(
                new Readiness(simulated.getId(), true, List.of()),
                new Readiness(never.getId(), false, List.of("Economic configuration not found")),
                new Readiness(running.getId(), true, List.of())));
        when(economics.configuration(simulated.getId())).thenReturn(configuration());
        var evaluation = new EconomicEvaluation(simulated, "{}");
        ReflectionTestUtils.setField(evaluation, "id", UUID.randomUUID());
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(simulated.getId())).thenReturn(List.of(evaluation));
        when(economics.getEvaluation(project.getId(), simulated.getId(), evaluation.getId()))
                .thenReturn(new EvaluationDTO(evaluation.getId(), Instant.now(), simulated(simulated, configuration())));
        var job = new EconomicJob(running, "{}", 10, "1");
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(job, "status", EconomicJobStatus.RUNNING);
        ReflectionTestUtils.setField(job, "completedRounds", 4);
        when(jobs.findByVersionIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(jobs.findByVersionIdOrderByCreatedAtDesc(running.getId())).thenReturn(List.of(job));

        when(access.resolveForProject(project.getId())).thenReturn(new ProjectAccessDTO(project.getId(), false, null,
                List.of(), false, false, List.of(versionAccess(simulated.getId(), true), versionAccess(never.getId(), false))));

        var statuses = service.list(project.getId());

        var first = statuses.stream().filter(s -> s.versionId().equals(simulated.getId())).findFirst().orElseThrow();
        assertEquals(ScenarioStatus.UP_TO_DATE, first.status());
        assertEquals(evaluation.getId(), first.latestEvaluationId());
        assertEquals(EvaluationMode.DETERMINISTIC, first.mode());
        assertEquals("Proyecto", first.projectName());
        assertTrue(first.canSimulate(), "the version access says it can be simulated");
        var unsimulated = statuses.stream().filter(s -> s.versionId().equals(never.getId())).findFirst().orElseThrow();
        assertEquals(ScenarioStatus.NEVER_SIMULATED, unsimulated.status());
        assertFalse(unsimulated.ready());
        assertEquals(List.of("Economic configuration not found"), unsimulated.readinessErrors());
        var simulating = statuses.stream().filter(s -> s.versionId().equals(running.getId())).findFirst().orElseThrow();
        assertEquals(ScenarioStatus.SIMULATING, simulating.status());
        assertEquals(4, simulating.job().completedRounds());
        assertEquals(10, simulating.job().rounds());

        assertFalse(unsimulated.canSimulate(), "no SIMULATE on that version");
        assertFalse(simulating.canSimulate(), "versions missing from the access answer cannot be simulated");
    }

    static Version version(String name) {
        var version = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        return version;
    }

    /** Per-version access as {@code VersionAccessService} answers it; only {@code canSimulate} matters here. */
    static VersionAccessDTO versionAccess(UUID versionId, boolean canSimulate) {
        return new VersionAccessDTO(versionId, "v", null, List.of(), null, List.of(),
                true, false, false, canSimulate, false, false, true, true, false);
    }
}
