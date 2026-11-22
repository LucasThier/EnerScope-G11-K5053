package org.enerscope.comparison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.comparison.dto.*;
import org.enerscope.comparison.model.ScenarioComparison;
import org.enerscope.comparison.model.ScenarioComparisonItem;
import org.enerscope.comparison.repository.ScenarioComparisonRepository;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.model.EconomicRound;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.repository.EconomicRoundRepository;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.scenario.service.ScenarioDefaults;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares the saved simulations of scenarios and stores comparison definitions.
 *
 * <p>A comparison holds scenarios of <b>one project</b> — the one in the path — whether they rest on the same version or on
 * different ones. Order of answers: {@code 403} for the whole request when the caller lacks {@code VIEW_PROJECT} there
 * (nothing is looked up, so there is never a partial result); then a generic {@code 404} "Scenario not found" for a scenario
 * that does not exist, is deleted, rests on an inactive version or belongs to another project; then {@code 400} (two to
 * eight scenarios, none twice, every one simulated and up to date). Saving also needs {@code EDIT_PROJECT}.</p>
 *
 * <p>A new comparison uses each scenario's <em>latest</em> simulation, only while it is up to date
 * ({@link ScenarioStatusService#isUpToDate(Version, Scenario, EvaluationDTO.Snapshot)}). The saved comparison stores, per item,
 * its evaluation (immutable), the scenario, and the names of the version and of the scenario as they were. Reopening it reads
 * each item through its <em>own</em> evaluation and does not need the scenario or the version to be active: an item whose
 * scenario or version was deleted is flagged {@code deleted} and still shows its saved data.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioComparisonService {

    public static final int MIN_SCENARIOS = 2;
    /** The categorical palette identifies at most eight scenarios without repeating a colour. */
    public static final int MAX_SCENARIOS = 8;
    private static final BigDecimal MAX_THRESHOLD = new BigDecimal("1000");

    private final EconomicService economics;
    private final ScenarioRepository scenarios;
    private final EconomicEvaluationRepository evaluations;
    private final EconomicJobRepository jobs;
    private final EconomicRoundRepository rounds;
    private final ScenarioComparisonRepository comparisons;
    private final ScenarioMetricsExtractor extractor;
    private final ScenarioComparisonCalculator calculator;
    private final ScenarioStatusService status;
    private final ScenarioDefaults scenarioDefaults;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    /** A scenario to compare, the version it rests on, and the simulation actually compared. */
    private record Resolved(Project project, Version version, Scenario scenario, EvaluationDTO evaluation) {}

    @Transactional(readOnly = true)
    public ComparisonResultDTO preview(UUID projectId, ComparisonRequestDTO request) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        List<Resolved> resolved = resolve(project, request);
        ComparisonResultDTO result = calculate(resolved, request.relevanceThreshold(), request.rateThreshold());
        logger.debug("Previewed comparison of {} scenarios in project {}", resolved.size(), projectId);
        return result;
    }

    public SavedComparisonDTO save(UUID projectId, ComparisonRequestDTO request) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        List<Resolved> resolved = resolve(project, request);
        BigDecimal relevance = orDefault(request.relevanceThreshold(), ScenarioComparisonCalculator.DEFAULT_RELEVANCE_THRESHOLD);
        BigDecimal rate = orDefault(request.rateThreshold(), ScenarioComparisonCalculator.DEFAULT_RATE_THRESHOLD);
        ComparisonResultDTO result = calculate(resolved, relevance, rate);

        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        ScenarioComparison comparison = new ScenarioComparison(project, name(request.name(), resolved), description, relevance, rate);
        for (int i = 0; i < resolved.size(); i++) {
            Resolved item = resolved.get(i);
            EconomicEvaluation evaluation = evaluations.findByIdAndScenarioId(item.evaluation().id(), item.scenario().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found"));
            var comparisonItem = new ScenarioComparisonItem(comparison, item.version(), evaluation, i);
            comparisonItem.assignScenario(item.scenario());
            comparison.addItem(comparisonItem);
        }
        ScenarioComparison stored = comparisons.saveAndFlush(comparison);
        logger.info("Scenario comparison {} saved in project {} with {} scenarios", stored.getId(), projectId, resolved.size());
        return dto(stored, result);
    }

    @Transactional(readOnly = true)
    public List<ComparisonSummaryDTO> list(UUID projectId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        return comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId).stream()
                .map(c -> new ComparisonSummaryDTO(c.getId(), c.getName(), c.getDescription(),
                        c.getItems().stream().map(ScenarioComparisonService::label).toList(),
                        c.getItems().stream().anyMatch(ScenarioComparisonService::deleted), c.getCreatedAt()))
                .toList();
    }

    /**
     * A saved comparison, recalculated from the immutable evaluations it was made with. Each item is read through its own
     * evaluation, so a deleted scenario or version never hides it; it is flagged and shown with its stored names.
     */
    @Transactional(readOnly = true)
    public SavedComparisonDTO get(UUID projectId, UUID comparisonId) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        ScenarioComparison comparison = find(projectId, comparisonId);
        List<Resolved> resolved = comparison.getItems().stream()
                .map(item -> new Resolved(project, item.getVersion(), item.getScenario(),
                        economics.evaluationById(item.getEvaluation().getId())))
                .toList();
        List<ScenarioComparisonItem> items = comparison.getItems();
        return dto(comparison, calculate(resolved, items, comparison.getRelevanceThreshold(), comparison.getRateThreshold()));
    }

    public void delete(UUID projectId, UUID comparisonId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        ScenarioComparison comparison = find(projectId, comparisonId);
        comparison.deactivate();
        comparisons.save(comparison);
        logger.info("Scenario comparison {} deleted from project {}", comparisonId, projectId);
    }

    // ---------------------------------------------------------------- internals

    /** The scenarios of the request, found (404), checked (400) and each paired with its latest up-to-date simulation. */
    private List<Resolved> resolve(Project project, ComparisonRequestDTO request) {
        List<ComparisonRequestDTO.Item> items = shape(request);
        List<Resolved> resolved = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        List<Located> located = new ArrayList<>();
        for (ComparisonRequestDTO.Item item : items) {
            located.add(locate(project, item));
        }
        if (located.size() < MIN_SCENARIOS) {
            throw new IllegalArgumentException("Pick at least " + MIN_SCENARIOS + " evaluations to compare");
        }
        for (Located each : located) {
            if (!seen.add(each.scenario().getId())) {
                throw new IllegalArgumentException("The same scenario cannot be compared with itself");
            }
        }
        threshold(request.relevanceThreshold(), "Relevance threshold");
        threshold(request.rateThreshold(), "Rate threshold");
        if (request.name() != null && request.name().trim().length() > 120) {
            throw new IllegalArgumentException("Comparison name must be at most 120 characters");
        }
        for (Located each : located) {
            Scenario scenario = each.scenario();
            Version version = each.version();
            EconomicEvaluation latest = evaluations.findByScenarioIdOrderByCreatedAtDesc(scenario.getId()).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Scenario has never been simulated: " + scenario.getName()));
            // Reuses the economic module's read, which also enriches historical snapshots saved without indicators.
            EvaluationDTO evaluation = economics.evaluationOf(scenario, latest.getId());
            if (!status.isUpToDate(version, scenario, evaluation.snapshot())) {
                logger.info("Scenario {} changed since its latest simulation {}; comparison refused", scenario.getId(), latest.getId());
                throw new IllegalArgumentException("Scenario changed since its last simulation: " + scenario.getName());
            }
            resolved.add(new Resolved(project, version, scenario, evaluation));
        }
        return resolved;
    }

    private record Located(Scenario scenario, Version version) {}

    /** The request's items, or 400 when the body, the items or an item is missing, or there are more than the maximum. */
    private List<ComparisonRequestDTO.Item> shape(ComparisonRequestDTO request) {
        if (request == null || request.items() == null) {
            throw new IllegalArgumentException("Pick at least " + MIN_SCENARIOS + " evaluations to compare");
        }
        // Bounded before anything is looked up, so a huge request cannot cost a lookup per item.
        if (request.items().size() > MAX_SCENARIOS) {
            throw new IllegalArgumentException("At most " + MAX_SCENARIOS + " evaluations can be compared at once");
        }
        for (ComparisonRequestDTO.Item item : request.items()) {
            if (item == null || (item.scenarioId() == null && item.versionId() == null)) {
                throw new IllegalArgumentException("Every compared item needs a scenario");
            }
        }
        return request.items();
    }

    /**
     * Finds the scenario of an item in the path project. An unknown, deleted or foreign scenario (or one whose version is
     * inactive or in another project) is one generic 404. An item that names only a version (the deprecated form) means that
     * version's default scenario.
     */
    @SuppressWarnings("deprecation")
    private Located locate(Project project, ComparisonRequestDTO.Item item) {
        Scenario scenario = item.scenarioId() != null
                ? scenarios.findById(item.scenarioId()).filter(Scenario::isActive).orElse(null)
                : scenarioDefaults.existing(item.versionId()).orElse(null);
        if (scenario == null) {
            logger.warn("A comparison named a scenario that does not exist or is not active");
            throw new EntityNotFoundException("Scenario not found");
        }
        UUID versionId = scenario.getVersion().getId();
        Version version = project.getVersions().stream().filter(v -> v.getId().equals(versionId) && v.isActive()).findFirst()
                .orElseThrow(() -> {
                    logger.warn("Scenario {} was compared from project {} but belongs to another project or an inactive version",
                            scenario.getId(), project.getId());
                    return new EntityNotFoundException("Scenario not found");
                });
        return new Located(scenario, version);
    }

    /** The given name, or the scenario names joined ("Base vs Ampliación"), cut to the column size. */
    private static String name(String requested, List<Resolved> resolved) {
        if (requested != null && !requested.isBlank()) return requested.trim();
        String joined = String.join(" vs ", resolved.stream().map(r -> r.scenario().getName()).toList());
        return joined.length() <= 120 ? joined : joined.substring(0, 119) + "…";
    }

    private static void threshold(BigDecimal value, String name) {
        if (value != null && (value.signum() <= 0 || value.compareTo(MAX_THRESHOLD) > 0)) {
            throw new IllegalArgumentException(name + " must be greater than 0 and at most " + MAX_THRESHOLD);
        }
    }

    private static BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private ComparisonResultDTO calculate(List<Resolved> resolved, BigDecimal relevance, BigDecimal rate) {
        return calculate(resolved, null, relevance, rate);
    }

    /** {@code stored} gives the names (and the deleted flag) a saved comparison keeps; a preview uses the live ones. */
    private ComparisonResultDTO calculate(List<Resolved> resolved, List<ScenarioComparisonItem> stored, BigDecimal relevance, BigDecimal rate) {
        List<ScenarioMetrics> measured = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            measured.add(extractor.extract(source(resolved.get(i), stored == null ? null : stored.get(i))));
        }
        return calculator.compare(measured, relevance, rate);
    }

    private ScenarioSource source(Resolved resolved, ScenarioComparisonItem item) {
        Version version = resolved.version();
        Project project = resolved.project();
        Scenario scenario = resolved.scenario();
        EvaluationDTO evaluation = resolved.evaluation();
        List<EconomicRoundResult> roundResults = List.of();
        if (evaluation.snapshot().schemaVersion() >= 3) {
            roundResults = jobs.findByEvaluationId(evaluation.id())
                    .filter(job -> job.getStatus() == EconomicJobStatus.COMPLETED)
                    .map(job -> rounds.findByJobIdOrderByRoundNumberAsc(job.getId()).stream().map(this::round).toList())
                    .orElseGet(() -> {
                        logger.warn("Probabilistic evaluation {} has no completed rounds to compare", evaluation.id());
                        return List.of();
                    });
        }
        String versionName = item != null && item.getVersionName() != null ? item.getVersionName() : version.getName();
        String scenarioName = item != null && item.getScenarioName() != null ? item.getScenarioName()
                : scenario != null ? scenario.getName() : null;
        boolean deleted = item != null && deleted(item);
        return new ScenarioSource(project.getId(), project.getName(), version.getId(), versionName,
                scenario == null ? null : scenario.getId(), scenarioName, deleted, evaluation.id(),
                evaluation.createdAt(), evaluation.snapshot(), roundResults);
    }

    private EconomicRoundResult round(EconomicRound round) {
        try {
            return mapper.readValue(round.getSnapshotJson(), EconomicRoundResult.class);
        } catch (JsonProcessingException error) {
            logger.error("Cannot read economic round {}", round.getId(), error);
            throw new IllegalStateException("Cannot read economic round", error);
        }
    }

    private ScenarioComparison find(UUID projectId, UUID comparisonId) {
        return comparisons.findByIdAndProjectIdAndActiveTrue(comparisonId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("Comparison not found"));
    }

    /** True when the item's scenario, or its version, has been deleted since the comparison was saved. */
    static boolean deleted(ScenarioComparisonItem item) {
        boolean scenarioGone = item.getScenario() != null && !item.getScenario().isActive();
        return scenarioGone || !item.getVersion().isActive();
    }

    /** "Version · Scenario" with the names the item stored (the live ones for rows saved before the names were kept). */
    static String label(ScenarioComparisonItem item) {
        String version = item.getVersionName() != null ? item.getVersionName() : item.getVersion().getName();
        String scenario = item.getScenarioName() != null ? item.getScenarioName()
                : item.getScenario() != null ? item.getScenario().getName() : null;
        return scenario == null ? version : version + " · " + scenario;
    }

    private static SavedComparisonDTO dto(ScenarioComparison comparison, ComparisonResultDTO result) {
        List<SavedComparisonDTO.Item> items = comparison.getItems().stream()
                .map(item -> new SavedComparisonDTO.Item(item.getScenario() == null ? null : item.getScenario().getId(),
                        item.getVersion().getId(), item.getEvaluation().getId(),
                        item.getScenarioName() != null ? item.getScenarioName()
                                : item.getScenario() != null ? item.getScenario().getName() : null,
                        item.getVersionName() != null ? item.getVersionName() : item.getVersion().getName(), deleted(item)))
                .toList();
        return new SavedComparisonDTO(comparison.getId(), comparison.getName(), comparison.getDescription(),
                comparison.getRelevanceThreshold(), comparison.getRateThreshold(), comparison.getCreatedAt(), items, result);
    }
}
