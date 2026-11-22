package org.enerscope.comparison.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import org.enerscope.economic.model.results.ProbabilisticSummary.NumericStatistics;

/**
 * One indicator of one scenario.
 *
 * @param value           the value, or the mean over the rounds where it exists; {@code null} when unavailable
 * @param status          {@code AVAILABLE}, or why there is no value (e.g. {@code NOT_RECOVERED_WITHIN_HORIZON},
 *                        {@code NO_INVESTMENT}, {@code NOT_AVAILABLE}); never replaced by zero
 * @param availableRounds realizations that produced a value
 * @param totalRounds     realizations in the evaluation (1 for deterministic evaluations)
 * @param distribution    spread over the rounds; {@code null} for deterministic evaluations
 */
public record MetricValueDTO(BigDecimal value,
                             String status,
                             int availableRounds,
                             int totalRounds,
                             NumericStatistics distribution) {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    @JsonIgnore
    public boolean isAvailable() {
        return value != null;
    }
}
