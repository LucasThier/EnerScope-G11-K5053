package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.enerscope.comparison.dto.ComparisonResultDTO.SeriesPoint;
import org.enerscope.comparison.dto.MetricValueDTO;
import org.enerscope.comparison.dto.NodeFlowDTO;
import org.enerscope.comparison.model.enums.ComparisonMetric;
import org.enerscope.comparison.model.enums.EvaluationMode;

/**
 * Everything the comparison needs from one evaluation, already measured. Built by
 * {@link ScenarioMetricsExtractor}; compared by {@link ScenarioComparisonCalculator}.
 *
 * @param nodes physical nodes keyed by identity (stable across version copies), in snapshot order
 */
public record ScenarioMetrics(UUID evaluationId,
                              UUID versionId,
                              String versionName,
                              Instant evaluatedAt,
                              EvaluationMode mode,
                              int rounds,
                              String currency,
                              Integer startYear,
                              Integer years,
                              BigDecimal wacc,
                              String simulationAlgorithm,
                              Integer simulationCalculationVersion,
                              Map<ComparisonMetric, MetricValueDTO> metrics,
                              Map<String, Node> nodes,
                              List<SeriesPoint> annual) {

    /** One physical node of the scenario. {@code flows} is {@code null} when the evaluation saved no flows for it. */
    public record Node(String key, String name, String nodeType, NodeFlowDTO flows) {}

    public MetricValueDTO metric(ComparisonMetric metric) {
        return metrics.get(metric);
    }

    /** True when some indicator could not be measured because the evaluation lacks the data. */
    public boolean incomplete() {
        return metrics.values().stream().anyMatch(value -> MetricValueDTO.NOT_AVAILABLE.equals(value.status()));
    }
}
