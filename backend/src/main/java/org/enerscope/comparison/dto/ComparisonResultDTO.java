package org.enerscope.comparison.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.comparison.model.enums.*;

/**
 * Two or more economic evaluations side by side. Every scenario is keyed by its
 * evaluation id, since two evaluations of the same version can be compared.
 *
 * @param findings relevant differences against the baseline, most significant first
 */
public record ComparisonResultDTO(UUID baselineEvaluationId,
                                  BigDecimal relevanceThreshold,
                                  BigDecimal rateThreshold,
                                  List<Scenario> scenarios,
                                  List<MetricRow> metrics,
                                  List<NodeRow> nodes,
                                  List<Series> annual,
                                  List<Warning> warnings,
                                  List<Finding> findings) {

    /**
     * One compared evaluation.
     *
     * @param bestIn indicators where this scenario has the most favorable value
     */
    public record Scenario(UUID evaluationId,
                           UUID versionId,
                           String versionName,
                           Instant evaluatedAt,
                           boolean baseline,
                           EvaluationMode mode,
                           int rounds,
                           String currency,
                           Integer startYear,
                           Integer years,
                           BigDecimal wacc,
                           List<ComparisonMetric> bestIn) {}

    /** One indicator across all scenarios. {@code bestEvaluationIds} is empty for neutral indicators. */
    public record MetricRow(ComparisonMetric metric,
                           String label,
                           MetricCategory category,
                           MetricUnit unit,
                           MetricPreference preference,
                           List<UUID> bestEvaluationIds,
                           boolean relevant,
                           List<MetricCell> values) {}

    /**
     * One scenario's value of an indicator.
     *
     * @param absoluteDelta value minus the baseline's; {@code null} for the baseline or when not comparable
     * @param relativeDelta absoluteDelta over |baseline| (fraction); {@code null} when the baseline is zero
     */
    public record MetricCell(UUID evaluationId,
                             MetricValueDTO value,
                             BigDecimal absoluteDelta,
                             BigDecimal relativeDelta,
                             DifferenceAssessment assessment,
                             boolean relevant) {}

    /** One physical node, matched across scenarios by its identity (stable across version copies). */
    public record NodeRow(String nodeKey,
                          String name,
                          String nodeType,
                          boolean relevant,
                          List<NodeCell> values) {}

    /**
     * One scenario's flows at a node. {@code flows} is {@code null} when the node is not in the scenario.
     *
     * @param outputDelta         output minus the baseline node's output
     * @param outputRelativeDelta outputDelta over the baseline output (fraction)
     */
    public record NodeCell(UUID evaluationId,
                           boolean present,
                           NodeFlowDTO flows,
                           BigDecimal outputDelta,
                           BigDecimal outputRelativeDelta,
                           boolean relevant) {}

    /** Year-by-year flows of one scenario, for charts. */
    public record Series(UUID evaluationId, List<SeriesPoint> points) {}

    /** Cash values are in the scenario currency; volumes in simulator units. Missing data stays {@code null}. */
    public record SeriesPoint(int year,
                              BigDecimal cashFlow,
                              BigDecimal cumulativeCashFlow,
                              BigDecimal producedVolume,
                              BigDecimal exportedVolume) {}

    public record Warning(ComparabilityWarning code, String message, List<UUID> evaluationIds) {}

    /**
     * A relevant difference of one scenario against the baseline.
     *
     * @param metric  {@code null} for node findings
     * @param nodeKey {@code null} for indicator findings
     * @param message Spanish, ready to show
     */
    public record Finding(FindingType type,
                          UUID evaluationId,
                          ComparisonMetric metric,
                          String nodeKey,
                          BigDecimal relativeDelta,
                          String message) {}
}
