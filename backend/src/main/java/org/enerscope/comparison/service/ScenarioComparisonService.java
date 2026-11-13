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
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares saved economic evaluations of one project and stores comparison
 * definitions. Reading needs {@code VIEW_PROJECT}; saving and deleting need
 * {@code EDIT_PROJECT}. Every compared version must belong to the project in the
 * path and every evaluation to its version, so a caller cannot read results of a
 * project they do not belong to by naming them.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioComparisonService {

    public static final int MIN_SCENARIOS = 2;
    public static final int MAX_SCENARIOS = 10;
    private static final BigDecimal MAX_THRESHOLD = new BigDecimal("1000");

    private final EconomicService economics;
    private final EconomicEvaluationRepository evaluations;
    private final EconomicJobRepository jobs;
    private final EconomicRoundRepository rounds;
    private final ScenarioComparisonRepository comparisons;
    private final ScenarioMetricsExtractor extractor;
    private final ScenarioComparisonCalculator calculator;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    /** A resolved item: the version and the evaluation actually compared. */
    private record Resolved(Version version, EconomicEvaluation evaluation) {}

    @Transactional(readOnly = true)
    public ComparisonResultDTO preview(UUID projectId, ComparisonRequestDTO request) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        validate(request, false);
        List<Resolved> resolved = resolve(project, request.items());
        ComparisonResultDTO result = calculate(projectId, resolved, request.relevanceThreshold(), request.rateThreshold());
        logger.debug("Previewed comparison of {} evaluations in project {}", resolved.size(), projectId);
        return result;
    }

    public SavedComparisonDTO save(UUID projectId, ComparisonRequestDTO request) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        validate(request, true);
        List<Resolved> resolved = resolve(project, request.items());
        BigDecimal relevance = orDefault(request.relevanceThreshold(), ScenarioComparisonCalculator.DEFAULT_RELEVANCE_THRESHOLD);
        BigDecimal rate = orDefault(request.rateThreshold(), ScenarioComparisonCalculator.DEFAULT_RATE_THRESHOLD);
        ComparisonResultDTO result = calculate(projectId, resolved, relevance, rate);

        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        ScenarioComparison comparison = new ScenarioComparison(project, request.name().trim(), description, relevance, rate);
        for (int i = 0; i < resolved.size(); i++) {
            comparison.addItem(new ScenarioComparisonItem(comparison, resolved.get(i).version(), resolved.get(i).evaluation(), i));
        }
        ScenarioComparison stored = comparisons.saveAndFlush(comparison);
        logger.info("Scenario comparison {} saved in project {} with {} evaluations", stored.getId(), projectId, resolved.size());
        return dto(stored, result);
    }

    @Transactional(readOnly = true)
    public List<ComparisonSummaryDTO> list(UUID projectId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        return comparisons.findByProjectIdAndActiveTrueOrderByCreatedAtDesc(projectId).stream()
                .map(c -> new ComparisonSummaryDTO(c.getId(), c.getName(), c.getDescription(), c.getItems().size(), c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedComparisonDTO get(UUID projectId, UUID comparisonId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        ScenarioComparison comparison = find(projectId, comparisonId);
        List<Resolved> resolved = comparison.getItems().stream()
                .map(item -> new Resolved(item.getVersion(), item.getEvaluation())).toList();
        return dto(comparison, calculate(projectId, resolved, comparison.getRelevanceThreshold(), comparison.getRateThreshold()));
    }

    public void delete(UUID projectId, UUID comparisonId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        ScenarioComparison comparison = find(projectId, comparisonId);
        comparison.deactivate();
        comparisons.save(comparison);
        logger.info("Scenario comparison {} deleted from project {}", comparisonId, projectId);
    }

    // ---------------------------------------------------------------- internals

    private void validate(ComparisonRequestDTO request, boolean saving) {
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
        if (saving) {
            if (request.name() == null || request.name().isBlank()) {
                throw new IllegalArgumentException("A saved comparison needs a name");
            }
            if (request.name().trim().length() > 120) {
                throw new IllegalArgumentException("Comparison name must be at most 120 characters");
            }
        }
    }

    private static void threshold(BigDecimal value, String name) {
        if (value != null && (value.signum() <= 0 || value.compareTo(MAX_THRESHOLD) > 0)) {
            throw new IllegalArgumentException(name + " must be greater than 0 and at most " + MAX_THRESHOLD);
        }
    }

    private static BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private List<Resolved> resolve(Project project, List<ComparisonRequestDTO.Item> items) {
        List<Resolved> resolved = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ComparisonRequestDTO.Item item : items) {
            Version version = project.getVersions().stream()
                    .filter(v -> v.getId().equals(item.versionId()) && v.isActive())
                    .findFirst()
                    .orElseThrow(() -> {
                        logger.warn("Version {} was compared under project {}, which does not own it", item.versionId(), project.getId());
                        return new EntityNotFoundException("Version does not belong to project");
                    });
            EconomicEvaluation evaluation = item.evaluationId() == null
                    ? evaluations.findByVersionIdOrderByCreatedAtDesc(version.getId()).stream().findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Version " + version.getName() + " has no economic evaluation to compare"))
                    : evaluations.findByIdAndVersionId(item.evaluationId(), version.getId())
                            .orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found"));
            if (!seen.add(evaluation.getId())) {
                throw new IllegalArgumentException("The same evaluation cannot be compared with itself");
            }
            resolved.add(new Resolved(version, evaluation));
        }
        return resolved;
    }

    private ComparisonResultDTO calculate(UUID projectId, List<Resolved> resolved, BigDecimal relevance, BigDecimal rate) {
        List<ScenarioMetrics> measured = resolved.stream().map(r -> extractor.extract(source(projectId, r))).toList();
        return calculator.compare(measured, relevance, rate);
    }

    private ScenarioSource source(UUID projectId, Resolved resolved) {
        Version version = resolved.version();
        // Reuses the economic module's read, which also enriches historical snapshots saved without indicators.
        EvaluationDTO evaluation = economics.getEvaluation(projectId, version.getId(), resolved.evaluation().getId());
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
        return new ScenarioSource(version.getId(), version.getName(), evaluation.id(), evaluation.createdAt(),
                evaluation.snapshot(), roundResults);
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
        List<ComparisonRequestDTO.Item> items = comparison.getItems().stream()
                .map(item -> new ComparisonRequestDTO.Item(item.getVersion().getId(), item.getEvaluation().getId()))
                .toList();
        return new SavedComparisonDTO(comparison.getId(), comparison.getName(), comparison.getDescription(),
                comparison.getRelevanceThreshold(), comparison.getRateThreshold(), comparison.getCreatedAt(), items, result);
    }
}
