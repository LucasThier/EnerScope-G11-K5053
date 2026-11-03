package org.enerscope.comparison.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable comparison contracts. Weights and probabilities use percentages. */
public final class ComparisonDTO {
    private ComparisonDTO() {}
    public enum Metric { NPV, IRR, PAYBACK, INVESTMENT, OPERATING_COST, DELIVERED_VOLUME }
    public record Context(String id, String name, BigDecimal probability) {}
    public record Cell(String contextId, UUID versionId, UUID evaluationId, List<UUID> deliveryNodeIds) {}
    public record Alternative(String id, String name, List<Cell> cells) {}
    public record Criterion(Metric metric, BigDecimal weight, BigDecimal unfavorable, BigDecimal favorable,
                            BigDecimal minimum, BigDecimal maximum) {}
    public record Request(String name, String scopeDescription, boolean assumptionsConfirmed,
                          List<Context> contexts, List<Alternative> alternatives, List<Criterion> criteria) {}
    public record Value(Metric metric, BigDecimal value, String unit, BigDecimal score,
                        BigDecimal contribution, String issue, boolean meetsLimits) {}
    public record ContextResult(String contextId, UUID versionId, UUID evaluationId, int rounds,
                                List<Value> values, BigDecimal score, boolean eligible, List<String> issues) {}
    public record AlternativeResult(String id, String name, List<ContextResult> contexts, BigDecimal score,
                                    BigDecimal worstScore, boolean eligible, Integer rank) {}
    public record Result(int calculationVersion, String status, String currency, int startYear, int years,
                         List<AlternativeResult> alternatives, List<String> winnerIds, List<String> issues) {}
    public record Saved(UUID id, Instant createdAt, Request configuration, Result result) {}
    public record Summary(UUID id, Instant createdAt, String name) {}
}
