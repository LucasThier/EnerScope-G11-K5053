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
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.scenario.service.ScenarioDefaults;
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
    final ScenarioRepository scenarios = mock(ScenarioRepository.class);
    final EconomicEvaluationRepository evaluations = mock(EconomicEvaluationRepository.class);
    final EconomicJobRepository jobs = mock(EconomicJobRepository.class);
    final EconomicRoundRepository rounds = mock(EconomicRoundRepository.class);
    final ScenarioComparisonRepository comparisons = mock(ScenarioComparisonRepository.class);
    final ScenarioStatusService status = mock(ScenarioStatusService.class);
    final ScenarioDefaults defaults = mock(ScenarioDefaults.class);
    final ScenarioComparisonService service = new ScenarioComparisonService(economics, scenarios, evaluations, jobs, rounds,
            comparisons, new ScenarioMetricsExtractor(new EconomicStatisticsCalculator()),
            new ScenarioComparisonCalculator(), status, defaults, MAPPER, mock(AppLogger.class));

    final UUID projectId = UUID.randomUUID();
    Project project;
    Version baseVersion;
    Version expansionVersion;
    Scenario base;            // the default scenario of baseVersion
    Scenario expansion;       // the default scenario of expansionVersion
    Scenario alternative;     // a second scenario of baseVersion
    EconomicEvaluation baseEvaluation;
    EconomicEvaluation expansionEvaluation;
    EconomicEvaluation alternativeEvaluation;

    static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    Version version(Project owner, String name) {
        Version version = withId(new Version(name, null, List.of(), List.of(), new ArrayList<>(), new ArrayList<>()), UUID.randomUUID());
        owner.addVersion(version);
        return version;
    }

    Scenario scenario(Version version, String name, boolean isDefault) {
        Scenario scenario = withId(new Scenario(version, name, isDefault), UUID.randomUUID());
        when(scenarios.findById(scenario.getId())).thenReturn(Optional.of(scenario));
        if (isDefault) when(defaults.existing(version.getId())).thenReturn(Optional.of(scenario));
        return scenario;
    }

    /** Makes a new evaluation the scenario's latest simulation, read through the economic module. */
    EconomicEvaluation latest(Scenario scenario, String npv) {
        return latest(scenario, snapshot(npv));
    }

    EconomicEvaluation latest(Scenario scenario, EvaluationDTO.Snapshot snapshot) {
        EconomicEvaluation evaluation = withId(new EconomicEvaluation(scenario.getVersion(), "{}"), UUID.randomUUID());
        evaluation.assignScenario(scenario);
        var dto = new EvaluationDTO(evaluation.getId(), Instant.parse("2026-11-01T00:00:00Z"), snapshot);
        when(evaluations.findByScenarioIdOrderByCreatedAtDesc(scenario.getId())).thenReturn(List.of(evaluation));
        when(evaluations.findByIdAndScenarioId(evaluation.getId(), scenario.getId())).thenReturn(Optional.of(evaluation));
        when(economics.evaluationOf(scenario, evaluation.getId())).thenReturn(dto);
        when(economics.evaluationById(evaluation.getId())).thenReturn(dto);
        return evaluation;
    }

    EvaluationDTO.Snapshot snapshot(String npv) {
        return deterministic(configuration("USD", 2030, 1, "0.1"),
                result(npv, new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1)), raw("100", "90"));
    }

    ComparisonRequestDTO request(String name, Scenario... compared) {
        return new ComparisonRequestDTO(name, null, null, null, java.util.Arrays.stream(compared).map(s -> new Item(s.getId())).toList());
    }

    @BeforeEach
    void setUp() {
        project = withId(new Project("Proyecto", "", null), projectId);
        baseVersion = version(project, "Base");
        expansionVersion = version(project, "Ampliación");
        base = scenario(baseVersion, "Base", true);
        expansion = scenario(expansionVersion, "Ampliación", true);
        alternative = scenario(baseVersion, "Conservador", false);
        baseEvaluation = latest(base, "1000");
        expansionEvaluation = latest(expansion, "1500");
        alternativeEvaluation = latest(alternative, "800");
        when(economics.authorizeProject(eq(projectId), any())).thenReturn(project);
        when(status.isUpToDate(any(), any(Scenario.class), any())).thenReturn(true);
    }

    @Test
    void previewChecksViewPermissionAndComparesEachScenarioThroughItsLatestSimulation() {
        var result = service.preview(projectId, request(null, base, expansion));

        verify(economics).authorizeProject(projectId, VIEW_PROJECT);
        assertEquals(baseEvaluation.getId(), result.scenarios().get(0).evaluationId());
        assertEquals(expansionEvaluation.getId(), result.scenarios().get(1).evaluationId());
        assertEquals("Ampliación", result.scenarios().get(1).scenarioName());
        assertEquals("Ampliación", result.scenarios().get(1).versionName());
        assertEquals(expansion.getId(), result.scenarios().get(1).scenarioId());
        assertFalse(result.scenarios().get(1).deleted());
        var npv = result.metrics().stream().filter(r -> r.metric() == NPV).findFirst().orElseThrow();
        assertEquals(0, bd("500").compareTo(npv.values().get(0).gapToBest()));
        assertEquals(List.of(expansionEvaluation.getId()), npv.bestEvaluationIds());
        verifyNoInteractions(comparisons);
    }

    @Test
    void scenariosOfTheSameVersionAreComparedWithEachOther() {
        var result = service.preview(projectId, request(null, base, alternative));

        assertEquals(baseVersion.getId(), result.scenarios().get(0).versionId());
        assertEquals(baseVersion.getId(), result.scenarios().get(1).versionId(), "both rest on the same version");
        assertEquals(List.of("Base", "Conservador"), result.scenarios().stream().map(s -> s.scenarioName()).toList());
        assertEquals(alternativeEvaluation.getId(), result.scenarios().get(1).evaluationId());
    }

    @Test
    void scenariosOfDifferentVersionsAreComparedAndSameNamedOnesShowTheirVersion() {
        var otherDefault = scenario(expansionVersion, "Base", false);   // same scenario name as `base`, other version
        latest(otherDefault, "1200");

        var result = service.preview(projectId, request(null, base, otherDefault));

        assertTrue(result.findings().stream().noneMatch(f -> f.message().contains("«Base»")),
                "two scenarios called Base are told apart by their version: " + result.findings());
    }

    @Test
    void scenarioThatChangedSinceItsLastSimulationMustBeSimulatedAgain() {
        when(status.isUpToDate(any(), eq(expansion), any())).thenReturn(false);

        var error = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base, expansion)));
        assertEquals("Scenario changed since its last simulation: Ampliación", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.save(projectId, request(null, base, expansion)));
        verify(comparisons, never()).saveAndFlush(any());
    }

    @Test
    void scenarioNeverSimulatedCannotBeCompared() {
        when(evaluations.findByScenarioIdOrderByCreatedAtDesc(expansion.getId())).thenReturn(List.of());
        var error = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base, expansion)));
        assertEquals("Scenario has never been simulated: Ampliación", error.getMessage());
    }

    @Test
    void anUnknownScenarioIsNotFoundBeforeAnyEvaluationIsRead() {
        var unknown = new Item(UUID.randomUUID());
        var error = assertThrows(EntityNotFoundException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, List.of(unknown, new Item(expansion.getId())))));
        assertEquals("Scenario not found", error.getMessage());
        verify(evaluations, never()).findByScenarioIdOrderByCreatedAtDesc(any());
    }

    @Test
    void aSingleUnknownScenarioIs404EvenThoughOneScenarioIsAlsoTooFew() {
        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, List.of(new Item(UUID.randomUUID())))));
    }

    @Test
    void scenarioOfAnotherProjectIsNotFoundEvenWhenTheCallerCanViewIt() {
        var otherId = UUID.randomUUID();
        var other = withId(new Project("Cuenca Austral", "", null), otherId);
        when(economics.authorizeProject(otherId, VIEW_PROJECT)).thenReturn(other);
        var austral = scenario(version(other, "Austral"), "Austral base", true);
        latest(austral, "700");

        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, request(null, base, austral)));
        assertThrows(EntityNotFoundException.class, () -> service.save(projectId, request(null, base, austral)));
        verify(economics, never()).authorizeProject(otherId, VIEW_PROJECT);
        verify(evaluations, never()).findByScenarioIdOrderByCreatedAtDesc(austral.getId());
    }

    @Test
    void aDeletedScenarioOrOneOfAnInactiveVersionIsTheSameGeneric404() {
        var gone = scenario(baseVersion, "Borrado", false);
        gone.deactivate();
        var orphan = scenario(version(project, "Versión borrada"), "Huérfano", true);
        orphan.getVersion().deactivate();

        for (Scenario hidden : List.of(gone, orphan)) {
            var error = assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, request(null, base, hidden)));
            assertEquals("Scenario not found", error.getMessage());
        }
    }

    @Test
    void withoutAccessToThePathProjectTheWholeRequestIsForbiddenAndNothingIsLookedUp() {
        when(economics.authorizeProject(projectId, VIEW_PROJECT)).thenThrow(new ForbiddenException("Project permission required: VIEW_PROJECT"));
        clearInvocations(scenarios, evaluations);

        assertThrows(ForbiddenException.class, () -> service.preview(projectId, request(null, base, expansion)));
        assertThrows(ForbiddenException.class, () -> service.preview(projectId, request(null, base, new Scenario(baseVersion, "Ajeno", false))));
        verify(scenarios, never()).findById(any());
        verify(evaluations, never()).findByScenarioIdOrderByCreatedAtDesc(any());
    }

    @Test
    void aRequestThatNamesOnlyAVersionComparesItsDefaultScenario() {
        var result = service.preview(projectId, new ComparisonRequestDTO(null, null, null, null,
                List.of(Item.ofVersion(baseVersion.getId()), new Item(expansion.getId()))));

        assertEquals(base.getId(), result.scenarios().get(0).scenarioId());
        assertEquals(baseEvaluation.getId(), result.scenarios().get(0).evaluationId());
    }

    @Test
    void aVersionOfAnotherProjectNamedByTheOldFormIsAGeneric404() {
        var other = withId(new Project("Ajeno", "", null), UUID.randomUUID());
        var foreignVersion = version(other, "Ajena");
        var foreign = scenario(foreignVersion, "Base", true);
        latest(foreign, "1");

        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, new ComparisonRequestDTO(null, null, null, null,
                List.of(new Item(base.getId()), Item.ofVersion(foreignVersion.getId())))));
        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, new ComparisonRequestDTO(null, null, null, null,
                List.of(new Item(base.getId()), Item.ofVersion(UUID.randomUUID())))));
    }

    @Test
    void fewerThanTwoItemsTheSameScenarioTwiceAndInvalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base)));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, null));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, new ComparisonRequestDTO(null, null, null, null, null)));
        var twice = assertThrows(IllegalArgumentException.class, () -> service.preview(projectId, request(null, base, base)));
        assertEquals("The same scenario cannot be compared with itself", twice.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, bd("0"), null, List.of(new Item(base.getId()), new Item(expansion.getId())))));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, List.of(new Item(base.getId()), new Item(null)))));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO("x".repeat(121), null, null, null, List.of(new Item(base.getId()), new Item(expansion.getId())))));
        var nine = new ArrayList<Item>();
        for (int i = 0; i < 9; i++) nine.add(new Item(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.preview(projectId,
                new ComparisonRequestDTO(null, null, null, null, nine)));
    }

    @Test
    void saveNeedsEditPermissionNamesItAfterItsScenariosAndStoresTheNamesAndSimulationsCompared() {
        when(comparisons.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        var saved = service.save(projectId, request(null, base, alternative));

        verify(economics).authorizeProject(projectId, EDIT_PROJECT);
        var captor = ArgumentCaptor.forClass(ScenarioComparison.class);
        verify(comparisons).saveAndFlush(captor.capture());
        var stored = captor.getValue();
        assertEquals("Base vs Conservador", stored.getName());
        assertEquals(0, ScenarioComparisonCalculator.DEFAULT_RELEVANCE_THRESHOLD.compareTo(stored.getRelevanceThreshold()));
        assertEquals(0, ScenarioComparisonCalculator.DEFAULT_RATE_THRESHOLD.compareTo(stored.getRateThreshold()));
        assertEquals(2, stored.getItems().size());
        assertEquals(0, stored.getItems().get(0).getPosition());
        assertSame(alternativeEvaluation, stored.getItems().get(1).getEvaluation());
        assertSame(alternative, stored.getItems().get(1).getScenario());
        assertEquals("Conservador", stored.getItems().get(1).getScenarioName(), "the scenario name is stored as it was when saved");
        assertEquals("Base", stored.getItems().get(1).getVersionName(), "and so is the version's");
        assertEquals(alternativeEvaluation.getId(), saved.items().get(1).evaluationId());
        assertEquals(alternative.getId(), saved.items().get(1).scenarioId());
        assertFalse(saved.items().get(1).deleted());
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
        var probabilisticEvaluation = latest(expansion, probabilistic(configuration("USD", 2030, 1, "0.1")));
        var job = new EconomicJob(expansionVersion, "{}", 2, "1");
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

    // ------------------------------------------------------------ saved comparisons

    ScenarioComparison saved(ScenarioComparisonItem... items) {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        for (var item : items) comparison.addItem(item);
        when(comparisons.findByIdAndProjectIdAndActiveTrue(comparison.getId(), projectId)).thenReturn(Optional.of(comparison));
        when(comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId)).thenReturn(List.of(comparison));
        return comparison;
    }

    ScenarioComparisonItem item(ScenarioComparison comparison, Version version, Scenario scenario, EconomicEvaluation evaluation, int position) {
        var item = new ScenarioComparisonItem(comparison, version, evaluation, position);
        item.assignScenario(scenario);
        return item;
    }

    @Test
    void savedComparisonShowsItsOwnSimulationsEvenIfTheScenariosChangedLater() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        comparison.addItem(item(comparison, baseVersion, base, baseEvaluation, 0));
        comparison.addItem(item(comparison, expansionVersion, expansion, expansionEvaluation, 1));
        when(comparisons.findByIdAndProjectIdAndActiveTrue(comparison.getId(), projectId)).thenReturn(Optional.of(comparison));
        when(comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId)).thenReturn(List.of(comparison));
        when(status.isUpToDate(any(), any(Scenario.class), any())).thenReturn(false);

        var read = service.get(projectId, comparison.getId());
        assertEquals("Guardada", read.name());
        assertEquals(baseEvaluation.getId(), read.result().scenarios().get(0).evaluationId());
        assertEquals(List.of("Base · Base", "Ampliación · Ampliación"), service.list(projectId).getFirst().scenarios());
        assertFalse(service.list(projectId).getFirst().hasDeletedScenarios());
        assertThrows(EntityNotFoundException.class, () -> service.get(projectId, UUID.randomUUID()));
    }

    @Test
    void aSavedComparisonReadsEachItemByItsOwnEvaluationAndKeepsTheNamesItWasSavedWith() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        comparison.addItem(item(comparison, baseVersion, base, baseEvaluation, 0));
        comparison.addItem(item(comparison, expansionVersion, expansion, expansionEvaluation, 1));
        when(comparisons.findByIdAndProjectIdAndActiveTrue(any(), eq(projectId))).thenReturn(Optional.of(comparison));
        base.rename("Renombrado después");   // renamed after the comparison was saved
        reset(scenarios);                    // and the live rows are no longer even looked up

        var read = service.get(projectId, comparison.getId());

        assertEquals("Base", read.items().get(0).scenarioName(), "the name it was saved with");
        assertEquals("Base", read.result().scenarios().get(0).scenarioName());
        verify(economics).evaluationById(baseEvaluation.getId());
        verify(economics, never()).evaluationOf(any(), any());
        verifyNoInteractions(scenarios);
    }

    @Test
    void aSavedComparisonWithADeletedScenarioStillOpensFlaggedAsDeleted() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        comparison.addItem(item(comparison, baseVersion, base, baseEvaluation, 0));
        comparison.addItem(item(comparison, baseVersion, alternative, alternativeEvaluation, 1));
        alternative.deactivate();     // the scenario is deleted after the comparison was saved
        when(comparisons.findByIdAndProjectIdAndActiveTrue(any(), eq(projectId))).thenReturn(Optional.of(comparison));
        when(comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId)).thenReturn(List.of(comparison));

        var read = service.get(projectId, comparison.getId());

        assertFalse(read.items().get(0).deleted());
        assertTrue(read.items().get(1).deleted());
        assertEquals("Conservador", read.items().get(1).scenarioName());
        assertTrue(read.result().scenarios().get(1).deleted(), "the result marks it too");
        assertEquals(alternativeEvaluation.getId(), read.result().scenarios().get(1).evaluationId(), "and still has its saved data");
        assertTrue(service.list(projectId).getFirst().hasDeletedScenarios());
    }

    @Test
    void aSavedComparisonWithADeletedVersionStillOpensFlaggedAsDeleted() {
        var comparison = withId(new ScenarioComparison(project, "Guardada", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        comparison.addItem(item(comparison, baseVersion, base, baseEvaluation, 0));
        comparison.addItem(item(comparison, expansionVersion, expansion, expansionEvaluation, 1));
        expansionVersion.deactivate();     // the version (and with it its scenarios) is deleted
        when(comparisons.findByIdAndProjectIdAndActiveTrue(any(), eq(projectId))).thenReturn(Optional.of(comparison));

        var read = service.get(projectId, comparison.getId());

        assertFalse(read.items().get(0).deleted());
        assertTrue(read.items().get(1).deleted());
        assertEquals("Ampliación", read.items().get(1).versionName());
        assertNotNull(read.result());
    }

    @Test
    void anOldComparisonWithoutScenarioOrNamesStillOpensThroughItsEvaluation() {
        var comparison = withId(new ScenarioComparison(project, "Vieja", null, bd("0.05"), bd("0.01")), UUID.randomUUID());
        // A row saved before scenarios existed and not yet linked: only the version and the evaluation are known.
        comparison.addItem(new ScenarioComparisonItem(comparison, baseVersion, baseEvaluation, 0));
        comparison.addItem(new ScenarioComparisonItem(comparison, expansionVersion, expansionEvaluation, 1));
        when(comparisons.findByIdAndProjectIdAndActiveTrue(any(), eq(projectId))).thenReturn(Optional.of(comparison));
        when(comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId)).thenReturn(List.of(comparison));

        var read = service.get(projectId, comparison.getId());

        assertNull(read.items().get(0).scenarioId());
        assertEquals("Base", read.items().get(0).versionName());
        assertNull(read.items().get(0).scenarioName());
        assertFalse(read.items().get(0).deleted());
        assertEquals(List.of("Base", "Ampliación"), service.list(projectId).getFirst().scenarios());
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
