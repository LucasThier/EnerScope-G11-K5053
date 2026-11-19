package org.enerscope.comparison.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.comparison.model.enums.*;

/**
 * Two or more scenarios compared with each other — none is a reference. For every
 * indicator each scenario gets its position (best, middle, worst) and its distance to
 * the best. Scenarios are keyed by evaluation id and keep the order they were picked in,
 * which only decides their colour on screen.
 *
 * @param findings relevant differences among the scenarios, most significant first
 */
public record ComparisonResultDTO(BigDecimal relevanceThreshold,
                                  BigDecimal rateThreshold,
                                  List<Scenario> scenarios,
                                  List<MetricRow> metrics,
                                  List<NodeRow> nodes,
                                  List<Series> annual,
                                  List<Warning> warnings,
                                  List<Finding> findings) {

    /**
     * One compared scenario.
     *
     * @param bestIn indicators where this scenario has the most favorable value
     */
    public record Scenario(UUID evaluationId,
                           UUID projectId,
                           String projectName,
                           UUID versionId,
                           String versionName,
                           Instant evaluatedAt,
                           EvaluationMode mode,
                           int rounds,
                           String currency,
                           Integer startYear,
                           Integer years,
                           BigDecimal wacc,
                           List<ComparisonMetric> bestIn) {}

    /**
     * One indicator across all scenarios. Best and worst are empty when the indicator is
     * not ranked (neutral, not comparable, fewer than two values) or every value ties.
     *
     * @param relevant the scenarios differ enough here to matter
     */
    public record MetricRow(ComparisonMetric metric,
                           String label,
                           MetricCategory category,
                           MetricUnit unit,
                           MetricPreference preference,
                           List<UUID> bestEvaluationIds,
                           List<UUID> worstEvaluationIds,
                           boolean relevant,
                           List<MetricCell> values) {}

    /**
     * One scenario's value of an indicator.
     *
     * @param rank              1 for the best; scenarios with equal values share a rank; {@code null} when unranked
     * @param gapToBest         how far from the best value, always ≥ 0, in the indicator's unit; {@code null} when unranked
     * @param relativeGapToBest gapToBest over |best| (fraction); {@code null} when unranked or the best is zero
     */
    public record MetricCell(UUID evaluationId,
                             MetricValueDTO value,
                             Integer rank,
                             BigDecimal gapToBest,
                             BigDecimal relativeGapToBest,
                             Standing standing) {}

    /** One physical node, matched across scenarios by its identity (stable across version copies). */
    public record NodeRow(String nodeKey,
                          String name,
                          String nodeType,
                          boolean relevant,
                          List<NodeCell> values) {}

    /** One scenario's flows at a node. {@code flows} is {@code null} when the node is not in the scenario. */
    public record NodeCell(UUID evaluationId, boolean present, NodeFlowDTO flows) {}

    /** Year-by-year flows of one scenario, for charts. */
    public record Series(UUID evaluationId, List<SeriesPoint> points) {}

    /** Cash values are in the scenario currency; volumes in simulator units. Missing data stays {@code null}. */
    public record SeriesPoint(int year,
                              BigDecimal cashFlow,
                              BigDecimal cumulativeCashFlow,
                              BigDecimal producedVolume,
                              BigDecimal exportedVolume) {}

    /** @param evaluationIds every scenario involved (all of them when the scenarios disagree on a setting) */
    public record Warning(ComparabilityWarning code, String message, List<UUID> evaluationIds) {}

    /**
     * A relevant difference among the scenarios.
     *
     * @param evaluationIds the scenarios it is about (e.g. best and worst, or those missing a value)
     * @param metric        {@code null} for node findings
     * @param nodeKey       {@code null} for indicator findings
     * @param relativeGap   size of the difference, for ordering; {@code null} when it has no relative size
     * @param message       Spanish, ready to show
     */
    public record Finding(FindingType type,
                          List<UUID> evaluationIds,
                          ComparisonMetric metric,
                          String nodeKey,
                          BigDecimal relativeGap,
                          String message) {}
}
