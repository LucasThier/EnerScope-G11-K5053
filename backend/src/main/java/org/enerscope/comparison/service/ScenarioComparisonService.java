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
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares saved economic evaluations and stores comparison definitions.
 *
 * <p>A comparison lives in the project of the path (where it is saved and listed),
 * but its scenarios may come from any project: each version is resolved to its own
 * project and the caller needs {@code VIEW_PROJECT} on every one of them, so naming
 * a version is never enough to read results of a project the caller cannot see.
 * Saving and deleting also need {@code EDIT_PROJECT} on the path project. Every
 * evaluation must belong to its version.</p>
 *
 * <p>A new comparison always uses each scenario's <em>latest</em> simulation, and only
 * while it is up to date ({@link ScenarioStatusService#isUpToDate}): a scenario that
 * changed since must be simulated again first. Reopening a saved comparison shows the
 * simulations it was made with, even if the scenarios changed afterwards.</p>
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
    private final ProjectRepository projects;
    private final EconomicEvaluationRepository evaluations;
    private final EconomicJobRepository jobs;
    private final EconomicRoundRepository rounds;
    private final ScenarioComparisonRepository comparisons;
    private final ScenarioMetricsExtractor extractor;
    private final ScenarioComparisonCalculator calculator;
    private final ScenarioStatusService status;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    /** A resolved item: the project that owns the version, the version and the simulation actually compared. */
    private record Resolved(Project project, Version version, EvaluationDTO evaluation) {}

    @Transactional(readOnly = true)
    public ComparisonResultDTO preview(UUID projectId, ComparisonRequestDTO request) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        validate(request);
        List<Resolved> resolved = resolve(project, request.items());
        ComparisonResultDTO result = calculate(resolved, request.relevanceThreshold(), request.rateThreshold());
        logger.debug("Previewed comparison of {} evaluations in project {}", resolved.size(), projectId);
        return result;
    }

    public SavedComparisonDTO save(UUID projectId, ComparisonRequestDTO request) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        validate(request);
        List<Resolved> resolved = resolve(project, request.items());
        BigDecimal relevance = orDefault(request.relevanceThreshold(), ScenarioComparisonCalculator.DEFAULT_RELEVANCE_THRESHOLD);
        BigDecimal rate = orDefault(request.rateThreshold(), ScenarioComparisonCalculator.DEFAULT_RATE_THRESHOLD);
        ComparisonResultDTO result = calculate(resolved, relevance, rate);

        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        ScenarioComparison comparison = new ScenarioComparison(project, name(request.name(), resolved), description, relevance, rate);
        for (int i = 0; i < resolved.size(); i++) {
            Resolved item = resolved.get(i);
            EconomicEvaluation evaluation = evaluations.findByIdAndVersionId(item.evaluation().id(), item.version().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found"));
            comparison.addItem(new ScenarioComparisonItem(comparison, item.version(), evaluation, i));
        }
        ScenarioComparison stored = comparisons.saveAndFlush(comparison);
        logger.info("Scenario comparison {} saved in project {} with {} evaluations", stored.getId(), projectId, resolved.size());
        return dto(stored, result);
    }

    @Transactional(readOnly = true)
    public List<ComparisonSummaryDTO> list(UUID projectId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        return comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId).stream()
                .map(c -> new ComparisonSummaryDTO(c.getId(), c.getName(), c.getDescription(),
                        c.getItems().stream().map(item -> item.getVersion().getName()).toList(), c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedComparisonDTO get(UUID projectId, UUID comparisonId) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        ScenarioComparison comparison = find(projectId, comparisonId);
        List<Resolved> resolved = comparison.getItems().stream()
                .map(item -> {
                    Project owner = owner(project, item.getVersion().getId());
                    return new Resolved(owner, item.getVersion(),
                            economics.getEvaluation(owner.getId(), item.getVersion().getId(), item.getEvaluation().getId()));
                })
                .toList();
        return dto(comparison, calculate(resolved, comparison.getRelevanceThreshold(), comparison.getRateThreshold()));
    }

    public void delete(UUID projectId, UUID comparisonId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        ScenarioComparison comparison = find(projectId, comparisonId);
        comparison.deactivate();
        comparisons.save(comparison);
        logger.info("Scenario comparison {} deleted from project {}", comparisonId, projectId);
    }

    // ---------------------------------------------------------------- internals

    private void validate(ComparisonRequestDTO request) {
        if (request == null || request.items() == null) {
            throw new IllegalArgumentException("Pick at least " + MIN_SCENARIOS + " evaluations to compare");
        }
        if (request.items().size() < MIN_SCENARIOS) {
            throw new IllegalArgumentException("Pick at least " + MIN_SCENARIOS + " evaluations to compare");
        }
        if (request.items().size() > MAX_SCENARIOS) {
            throw new IllegalArgumentException("At most " + MAX_SCENARIOS + " evaluations can be compared at once");
        }
        for (ComparisonRequestDTO.Item item : request.items()) {
            if (item == null || item.versionId() == null) {
                throw new IllegalArgumentException("Every compared item needs a version");
            }
        }
        threshold(request.relevanceThreshold(), "Relevance threshold");
        threshold(request.rateThreshold(), "Rate threshold");
        if (request.name() != null && request.name().trim().length() > 120) {
            throw new IllegalArgumentException("Comparison name must be at most 120 characters");
        }
    }

    /** The given name, or the scenario names joined ("Base vs Ampliación"), cut to the column size. */
    private static String name(String requested, List<Resolved> resolved) {
        if (requested != null && !requested.isBlank()) return requested.trim();
        String joined = String.join(" vs ", resolved.stream().map(r -> r.version().getName()).toList());
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

    /**
     * The project that owns a version, checked for {@code VIEW_PROJECT}. The path project
     * is already authorized; any other one is authorized here, so a version of a project
     * the caller cannot see answers 403 and an unknown or deleted version 404.
     */
    private Project owner(Project pathProject, UUID versionId) {
        UUID ownerId = projects.findIdByVersionId(versionId).orElseThrow(() -> {
            logger.warn("Version {} was compared but does not exist or is not active", versionId);
            return new EntityNotFoundException("Version not found");
        });
        return ownerId.equals(pathProject.getId()) ? pathProject
                : economics.authorizeProject(ownerId, ProjectMemberPermission.VIEW_PROJECT);
    }

    private List<Resolved> resolve(Project pathProject, List<ComparisonRequestDTO.Item> items) {
        List<Resolved> resolved = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ComparisonRequestDTO.Item item : items) {
            Project project = owner(pathProject, item.versionId());
            Version version = project.getVersions().stream()
                    .filter(v -> v.getId().equals(item.versionId()) && v.isActive())
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Version not found"));
            if (!seen.add(version.getId())) {
                throw new IllegalArgumentException("The same scenario cannot be compared with itself");
            }
            EconomicEvaluation latest = evaluations.findByVersionIdOrderByCreatedAtDesc(version.getId()).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Scenario has never been simulated: " + version.getName()));
            // Reuses the economic module's read, which also enriches historical snapshots saved without indicators.
            EvaluationDTO evaluation = economics.getEvaluation(project.getId(), version.getId(), latest.getId());
            if (!status.isUpToDate(version, evaluation.snapshot())) {
                logger.info("Scenario {} changed since its latest simulation {}; comparison refused", version.getId(), latest.getId());
                throw new IllegalArgumentException("Scenario changed since its last simulation: " + version.getName());
            }
            resolved.add(new Resolved(project, version, evaluation));
        }
        return resolved;
    }

    private ComparisonResultDTO calculate(List<Resolved> resolved, BigDecimal relevance, BigDecimal rate) {
        List<ScenarioMetrics> measured = resolved.stream().map(r -> extractor.extract(source(r))).toList();
        return calculator.compare(measured, relevance, rate);
    }

    private ScenarioSource source(Resolved resolved) {
        Version version = resolved.version();
        Project project = resolved.project();
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
        return new ScenarioSource(project.getId(), project.getName(), version.getId(), version.getName(), evaluation.id(),
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

    private static SavedComparisonDTO dto(ScenarioComparison comparison, ComparisonResultDTO result) {
        List<SavedComparisonDTO.Item> items = comparison.getItems().stream()
                .map(item -> new SavedComparisonDTO.Item(item.getVersion().getId(), item.getEvaluation().getId()))
                .toList();
        return new SavedComparisonDTO(comparison.getId(), comparison.getName(), comparison.getDescription(),
                comparison.getRelevanceThreshold(), comparison.getRateThreshold(), comparison.getCreatedAt(), items, result);
    }
}
