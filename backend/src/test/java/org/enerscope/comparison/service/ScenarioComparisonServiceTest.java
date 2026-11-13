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
import static org.mockito.Mockito.*;

class ScenarioComparisonServiceTest {

    final EconomicService economics = mock(EconomicService.class);
    final EconomicEvaluationRepository evaluations = mock(EconomicEvaluationRepository.class);
    final EconomicJobRepository jobs = mock(EconomicJobRepository.class);
    final EconomicRoundRepository rounds = mock(EconomicRoundRepository.class);
    final ScenarioComparisonRepository comparisons = mock(ScenarioComparisonRepository.class);
    final ScenarioComparisonService service = new ScenarioComparisonService(economics, evaluations, jobs, rounds,
            comparisons, new ScenarioMetricsExtractor(new EconomicStatisticsCalculator()),
            new ScenarioComparisonCalculator(), MAPPER, mock(AppLogger.class));

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

    Version version(String name) {
        Version version = withId(new Version(name, null, List.of(), List.of(), new ArrayList<>(), new ArrayList<>()), UUID.randomUUID());
        project.addVersion(version);
        return version;
    }

    void evaluationReturns(Version version, EconomicEvaluation evaluation, EvaluationDTO.Snapshot snapshot) {
        when(economics.getEvaluation(projectId, version.getId(), evaluation.getId()))
                .thenReturn(new EvaluationDTO(evaluation.getId(), Instant.parse("2026-11-01T00:00:00Z"), snapshot));
    }

    EvaluationDTO.Snapshot snapshot(String npv) {
        return deterministic(configuration("USD", 2030, 1, "0.1"),
                result(npv, new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1)), raw("100", "90"));
    }

    @BeforeEach
    void setUp() {
        project = withId(new Project("Proyecto", "", null), projectId);
        base = version("Base");
        expansion = version("Ampliación");
        baseEvaluation = withId(new EconomicEvaluation(base, "{}"), UUID.randomUUID());
        expansionEvaluation = withId(new EconomicEvaluation(expansion, "{}"), UUID.randomUUID());
        when(economics.authorizeProject(eq(projectId), any())).thenReturn(project);
        when(evaluations.findByIdAndVersionId(baseEvaluation.getId(), base.getId())).thenReturn(Optional.of(baseEvaluation));
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(expansion.getId())).thenReturn(List.of(expansionEvaluation));
        evaluationReturns(base, baseEvaluation, snapshot("1000"));
        evaluationReturns(expansion, expansionEvaluation, snapshot("1500"));
    }

    ComparisonRequestDTO request(String name, Item... items) {
        return new ComparisonRequestDTO(name, null, null, null, List.of(items));
    }

    @Test
    void previewChecksViewPermissionAndUsesTheLatestEvaluationWhenNoneIsGiven() {
        var result = service.preview(projectId, request(null,
                new Item(base.getId(), baseEvaluation.getId()), new Item(expansion.getId(), null)));

        verify(economics).authorizeProject(projectId, VIEW_PROJECT);
        assertEquals(baseEvaluation.getId(), result.baselineEvaluationId());
        assertEquals(expansionEvaluation.getId(), result.scenarios().get(1).evaluationId());
        assertEquals("Ampliación", result.scenarios().get(1).versionName());
        var npv = result.metrics().stream().filter(r -> r.metric() == NPV).findFirst().orElseThrow();
        assertEquals(0, bd("500").compareTo(npv.values().get(1).absoluteDelta()));
        verifyNoInteractions(comparisons);
    }

    @Test
    void versionOfAnotherProjectIsRejectedBeforeReadingAnyEvaluation() {
        var foreign = new Item(UUID.randomUUID(), null);
        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, request(null, foreign,
                new Item(expansion.getId(), null))));
        verifyNoInteractions(evaluations);
    }

    @Test
    void evaluationOfAnotherVersionIsRejected() {
        var mismatched = new Item(expansion.getId(), baseEvaluation.getId());
        when(evaluations.findByIdAndVersionId(baseEvaluation.getId(), expansion.getId())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, request(null,
                new Item(base.getId(), baseEvaluation.getId()), mismatched)));
    }

    @Test
    void versionWithoutEvaluationsCannotBeCompared() {
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(expansion.getId())).thenReturn(List.of());
        var error = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null,
                new Item(base.getId(), baseEvaluation.getId()), new Item(expansion.getId(), null))));
        assertTrue(error.getMessage().contains("no economic evaluation"));
    }

    @Test
    void fewerThanTwoItemsDuplicatesAndInvalidThresholdsAreRejected() {
        var one = new Item(base.getId(), baseEvaluation.getId());
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, one)));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, null));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, one, one)));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, bd("0"), null, List.of(one, new Item(expansion.getId(), null)))));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, one, new Item(null, null))));
        var eleven = new ArrayList<Item>();
        for (int i = 0; i < 11; i++) eleven.add(new Item(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, eleven)));
    }

    @Test
    void saveNeedsEditPermissionAndANameAndStoresResolvedEvaluationsInOrder() {
        when(comparisons.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.save(projectId, request(" ",
                new Item(base.getId(), baseEvaluation.getId()), new Item(expansion.getId(), null))));

        var saved = service.save(projectId, request("  Base vs ampliación ",
                new Item(base.getId(), baseEvaluation.getId()), new Item(expansion.getId(), null)));

        verify(economics, atLeastOnce()).authorizeProject(projectId, EDIT_PROJECT);
        var captor = ArgumentCaptor.forClass(ScenarioComparison.class);
        verify(comparisons).saveAndFlush(captor.capture());
        var stored = captor.getValue();
        assertEquals("Base vs ampliación", stored.getName());
        assertEquals(0, ScenarioComparisonCalculator.DEFAULT_RELEVANCE_THRESHOLD.compareTo(stored.getRelevanceThreshold()));
        assertEquals(0, ScenarioComparisonCalculator.DEFAULT_RATE_THRESHOLD.compareTo(stored.getRateThreshold()));
        assertEquals(2, stored.getItems().size());
        assertEquals(0, stored.getItems().get(0).getPosition());
        assertSame(expansionEvaluation, stored.getItems().get(1).getEvaluation());
        // The latest evaluation is resolved and stored, so reopening it later compares the same results.
        assertEquals(expansionEvaluation.getId(), saved.items().get(1).evaluationId());
        assertNotNull(saved.result());
    }

    @Test
    void userWithoutEditPermissionCannotSave() {
        when(economics.authorizeProject(projectId, EDIT_PROJECT)).thenThrow(new ForbiddenException("Project permission required"));
        assertThrows(ForbiddenException.class, () -> service.save(projectId, request("Nombre",
                new Item(base.getId(), baseEvaluation.getId()), new Item(expansion.getId(), null))));
        verifyNoInteractions(comparisons);
    }

    @Test
    void probabilisticEvaluationsAreComparedFromTheirSavedRounds() throws Exception {
        evaluationReturns(expansion, expansionEvaluation, probabilistic(configuration("USD", 2030, 1, "0.1")));
        var job = new EconomicJob(expansion, "{}", 2, "1");
        ReflectionTestUtils.setField(job, "status", EconomicJobStatus.COMPLETED);
        withId(job, UUID.randomUUID());
        when(jobs.findByEvaluationId(expansionEvaluation.getId())).thenReturn(Optional.of(job));
        List<EconomicRound> saved = new ArrayList<>();
        int number = 1;
        for (String npv : List.of("1000", "3000")) {
            var round = new EconomicRoundResult(number, "1", raw("100", "90"),
                    result(npv, new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1)));
            saved.add(new EconomicRound(job, number++, MAPPER.writeValueAsString(round), "{}"));
        }
        when(rounds.findByJobIdOrderByRoundNumberAsc(job.getId())).thenReturn(saved);

        var result = service.preview(projectId, request(null,
                new Item(base.getId(), baseEvaluation.getId()), new Item(expansion.getId(), null)));

        assertEquals(EvaluationMode.PROBABILISTIC, result.scenarios().get(1).mode());
        assertEquals(2, result.scenarios().get(1).rounds());
        var npv = result.metrics().stream().filter(r -> r.metric() == NPV).findFirst().orElseThrow().values().get(1);
        assertEquals(0, bd("2000").compareTo(npv.value().value()));
    }

    @Test
    void savedComparisonIsRecalculatedOnReadAndUnknownIdsAreNotFound() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        comparison.addItem(new org.enerscope.comparison.model.ScenarioComparisonItem(comparison, base, baseEvaluation, 0));
        comparison.addItem(new org.enerscope.comparison.model.ScenarioComparisonItem(comparison, expansion, expansionEvaluation, 1));
        when(comparisons.findByIdAndProjectIdAndActiveTrue(comparison.getId(), projectId)).thenReturn(Optional.of(comparison));
        when(comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId)).thenReturn(List.of(comparison));

        var read = service.get(projectId, comparison.getId());
        assertEquals("Guardada", read.name());
        assertEquals(baseEvaluation.getId(), read.result().baselineEvaluationId());
        assertEquals(2, service.list(projectId).getFirst().scenarioCount());
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
