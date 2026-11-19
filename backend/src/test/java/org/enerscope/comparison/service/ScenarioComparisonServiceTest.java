package org.enerscope.comparison.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.comparison.dto.ComparisonRequestDTO;
import org.enerscope.comparison.dto.ComparisonRequestDTO.Item;
import org.enerscope.comparison.model.ScenarioComparison;
import org.enerscope.comparison.model.ScenarioComparisonItem;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.comparison.repository.ScenarioComparisonRepository;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.EconomicRound;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.model.enums.IrrStatus;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.economic.model.results.IrrResult;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.repository.EconomicRoundRepository;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.economic.service.EconomicStatisticsCalculator;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.Project;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.enerscope.comparison.model.enums.ComparisonMetric.NPV;
import static org.enerscope.comparison.service.ComparisonFixtures.*;
import static org.enerscope.project.model.enums.ProjectMemberPermission.EDIT_PROJECT;
import static org.enerscope.project.model.enums.ProjectMemberPermission.VIEW_PROJECT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScenarioComparisonServiceTest {

    final EconomicService economics = mock(EconomicService.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final EconomicEvaluationRepository evaluations = mock(EconomicEvaluationRepository.class);
    final EconomicJobRepository jobs = mock(EconomicJobRepository.class);
    final EconomicRoundRepository rounds = mock(EconomicRoundRepository.class);
    final ScenarioComparisonRepository comparisons = mock(ScenarioComparisonRepository.class);
    final ScenarioStatusService status = mock(ScenarioStatusService.class);
    final ScenarioComparisonService service = new ScenarioComparisonService(economics, projects, evaluations, jobs, rounds,
            comparisons, new ScenarioMetricsExtractor(new EconomicStatisticsCalculator()),
            new ScenarioComparisonCalculator(), status, MAPPER, mock(AppLogger.class));

    final UUID projectId = UUID.randomUUID();
    Project project;
    Version base;
    Version expansion;
    EconomicEvaluation baseEvaluation;
    EconomicEvaluation expansionEvaluation;

    static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    Version version(Project owner, String name) {
        Version version = withId(new Version(name, null, List.of(), List.of(), new ArrayList<>(), new ArrayList<>()), UUID.randomUUID());
        owner.addVersion(version);
        when(projects.findIdByVersionId(version.getId())).thenReturn(Optional.of(owner.getId()));
        return version;
    }

    /** Makes {@code evaluation} the version's latest simulation, read through the economic module. */
    EconomicEvaluation latest(UUID owner, Version version, String npv) {
        return latest(owner, version, snapshot(npv));
    }

    EconomicEvaluation latest(UUID owner, Version version, EvaluationDTO.Snapshot snapshot) {
        EconomicEvaluation evaluation = withId(new EconomicEvaluation(version, "{}"), UUID.randomUUID());
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(version.getId())).thenReturn(List.of(evaluation));
        when(evaluations.findByIdAndVersionId(evaluation.getId(), version.getId())).thenReturn(Optional.of(evaluation));
        when(economics.getEvaluation(owner, version.getId(), evaluation.getId()))
                .thenReturn(new EvaluationDTO(evaluation.getId(), Instant.parse("2026-11-01T00:00:00Z"), snapshot));
        return evaluation;
    }

    EvaluationDTO.Snapshot snapshot(String npv) {
        return deterministic(configuration("USD", 2030, 1, "0.1"),
                result(npv, new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1)), raw("100", "90"));
    }

    ComparisonRequestDTO request(String name, Version... versions) {
        return new ComparisonRequestDTO(name, null, null, null, java.util.Arrays.stream(versions).map(v -> new Item(v.getId())).toList());
    }

    @BeforeEach
    void setUp() {
        project = withId(new Project("Proyecto", "", null), projectId);
        base = version(project, "Base");
        expansion = version(project, "Ampliación");
        baseEvaluation = latest(projectId, base, "1000");
        expansionEvaluation = latest(projectId, expansion, "1500");
        when(economics.authorizeProject(eq(projectId), any())).thenReturn(project);
        when(status.isUpToDate(any(), any())).thenReturn(true);
    }

    @Test
    void previewChecksViewPermissionAndComparesEachScenarioThroughItsLatestSimulation() {
        var result = service.preview(projectId, request(null, base, expansion));

        verify(economics).authorizeProject(projectId, VIEW_PROJECT);
        assertEquals(baseEvaluation.getId(), result.scenarios().get(0).evaluationId());
        assertEquals(expansionEvaluation.getId(), result.scenarios().get(1).evaluationId());
        assertEquals("Ampliación", result.scenarios().get(1).versionName());
        var npv = result.metrics().stream().filter(r -> r.metric() == NPV).findFirst().orElseThrow();
        assertEquals(0, bd("500").compareTo(npv.values().get(0).gapToBest()));
        assertEquals(List.of(expansionEvaluation.getId()), npv.bestEvaluationIds());
        verifyNoInteractions(comparisons);
    }

    @Test
    void scenarioThatChangedSinceItsLastSimulationMustBeSimulatedAgain() {
        when(status.isUpToDate(eq(expansion), any())).thenReturn(false);

        var error = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base, expansion)));
        assertEquals("Scenario changed since its last simulation: Ampliación", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.save(projectId, request(null, base, expansion)));
        verify(comparisons, never()).saveAndFlush(any());
    }

    @Test
    void scenarioNeverSimulatedCannotBeCompared() {
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(expansion.getId())).thenReturn(List.of());
        var error = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base, expansion)));
        assertEquals("Scenario has never been simulated: Ampliación", error.getMessage());
    }

    @Test
    void unknownVersionIsNotFoundBeforeReadingAnyEvaluation() {
        var unknown = new Item(UUID.randomUUID());
        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, List.of(unknown, new Item(expansion.getId())))));
        verify(evaluations, never()).findByVersionIdOrderByCreatedAtDesc(any());
    }

    @Test
    void scenariosOfAnotherProjectAreComparedWhenTheCallerCanViewIt() {
        var otherId = UUID.randomUUID();
        var other = withId(new Project("Cuenca Austral", "", null), otherId);
        when(economics.authorizeProject(otherId, VIEW_PROJECT)).thenReturn(other);
        var austral = version(other, "Austral base");
        var australEvaluation = latest(otherId, austral, "700");

        var result = service.preview(projectId, request(null, base, austral));

        verify(economics).authorizeProject(otherId, VIEW_PROJECT);
        verify(economics).getEvaluation(otherId, austral.getId(), australEvaluation.getId());
        assertEquals("Cuenca Austral", result.scenarios().get(1).projectName());
        assertEquals("Proyecto", result.scenarios().get(0).projectName());
    }

    @Test
    void scenarioOfAProjectTheCallerCannotViewIsForbidden() {
        var otherId = UUID.randomUUID();
        var other = withId(new Project("Ajeno", "", null), otherId);
        var hidden = version(other, "Oculto");
        when(economics.authorizeProject(otherId, VIEW_PROJECT)).thenThrow(new ForbiddenException("Project permission required: VIEW_PROJECT"));

        assertThrows(ForbiddenException.class, () -> service.preview(projectId, request(null, base, hidden)));
        verify(evaluations, never()).findByVersionIdOrderByCreatedAtDesc(hidden.getId());
    }

    @Test
    void fewerThanTwoItemsTheSameScenarioTwiceAndInvalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base)));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, null));
        var twice = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base, base)));
        assertEquals("The same scenario cannot be compared with itself", twice.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, bd("0"), null, List.of(new Item(base.getId()), new Item(expansion.getId())))));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, List.of(new Item(base.getId()), new Item(null)))));
        var nine = new ArrayList<Item>();
        for (int i = 0; i < 9; i++) nine.add(new Item(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, nine)));
    }

    @Test
    void saveNeedsEditPermissionNamesItAfterItsScenariosAndStoresTheSimulationsCompared() {
        when(comparisons.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        var saved = service.save(projectId, request(null, base, expansion));

        verify(economics).authorizeProject(projectId, EDIT_PROJECT);
        var captor = ArgumentCaptor.forClass(ScenarioComparison.class);
        verify(comparisons).saveAndFlush(captor.capture());
        var stored = captor.getValue();
        assertEquals("Base vs Ampliación", stored.getName());
        assertEquals(0, ScenarioComparisonCalculator.DEFAULT_RELEVANCE_THRESHOLD.compareTo(stored.getRelevanceThreshold()));
        assertEquals(0, ScenarioComparisonCalculator.DEFAULT_RATE_THRESHOLD.compareTo(stored.getRateThreshold()));
        assertEquals(2, stored.getItems().size());
        assertEquals(0, stored.getItems().get(0).getPosition());
        assertSame(expansionEvaluation, stored.getItems().get(1).getEvaluation());
        assertEquals(expansionEvaluation.getId(), saved.items().get(1).evaluationId());
        assertNotNull(saved.result());

        service.save(projectId, request("  Mi comparación ", base, expansion));
        verify(comparisons, times(2)).saveAndFlush(captor.capture());
        assertEquals("Mi comparación", captor.getValue().getName());
    }

    @Test
    void userWithoutEditPermissionCannotSave() {
        when(economics.authorizeProject(projectId, EDIT_PROJECT)).thenThrow(new ForbiddenException("Project permission required"));
        assertThrows(ForbiddenException.class, () -> service.save(projectId, request(null, base, expansion)));
        verifyNoInteractions(comparisons);
    }

    @Test
    void probabilisticSimulationsAreComparedFromTheirSavedRounds() throws Exception {
        var probabilisticEvaluation = latest(projectId, expansion, probabilistic(configuration("USD", 2030, 1, "0.1")));
        var job = new EconomicJob(expansion, "{}", 2, "1");
        ReflectionTestUtils.setField(job, "status", EconomicJobStatus.COMPLETED);
        withId(job, UUID.randomUUID());
        when(jobs.findByEvaluationId(probabilisticEvaluation.getId())).thenReturn(Optional.of(job));
        List<EconomicRound> saved = new ArrayList<>();
        int number = 1;
        for (String npv : List.of("1000", "3000")) {
            var round = new EconomicRoundResult(number, "1", raw("100", "90"),
                    result(npv, new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1)));
            saved.add(new EconomicRound(job, number++, MAPPER.writeValueAsString(round), "{}"));
        }
        when(rounds.findByJobIdOrderByRoundNumberAsc(job.getId())).thenReturn(saved);

        var result = service.preview(projectId, request(null, base, expansion));

        assertEquals(EvaluationMode.PROBABILISTIC, result.scenarios().get(1).mode());
        assertEquals(2, result.scenarios().get(1).rounds());
        var npv = result.metrics().stream().filter(r -> r.metric() == NPV).findFirst().orElseThrow().values().get(1);
        assertEquals(0, bd("2000").compareTo(npv.value().value()));
    }

    @Test
    void savedComparisonShowsItsOwnSimulationsEvenIfTheScenariosChangedLater() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        comparison.addItem(new ScenarioComparisonItem(comparison, base, baseEvaluation, 0));
        comparison.addItem(new ScenarioComparisonItem(comparison, expansion, expansionEvaluation, 1));
        when(comparisons.findByIdAndProjectIdAndActiveTrue(comparison.getId(), projectId)).thenReturn(Optional.of(comparison));
        when(comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId)).thenReturn(List.of(comparison));
        when(status.isUpToDate(any(), any())).thenReturn(false);

        var read = service.get(projectId, comparison.getId());
        assertEquals("Guardada", read.name());
        assertEquals(baseEvaluation.getId(), read.result().scenarios().get(0).evaluationId());
        assertEquals(List.of("Base", "Ampliación"), service.list(projectId).getFirst().scenarios());
        assertThrows(EntityNotFoundException.class, () -> service.get(projectId, UUID.randomUUID()));
    }

    @Test
    void deleteIsASoftDeleteThatNeedsEditPermission() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        when(comparisons.findByIdAndProjectIdAndActiveTrue(comparison.getId(), projectId)).thenReturn(Optional.of(comparison));

        service.delete(projectId, comparison.getId());

        verify(economics).authorizeProject(projectId, EDIT_PROJECT);
        assertFalse(comparison.isActive());
        verify(comparisons).save(comparison);
        verify(comparisons, never()).delete(any());
    }
}
