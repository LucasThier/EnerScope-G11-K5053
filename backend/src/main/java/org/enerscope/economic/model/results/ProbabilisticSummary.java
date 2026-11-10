package org.enerscope.economic.model.results;
import java.math.BigDecimal;
import java.util.*;

public record ProbabilisticSummary(int rounds,
                                   NumericStatistics npv,
                                   BigDecimal positiveNpvProbability,
                                   NumericStatistics irr,
                                   Map<String, Long> irrStatuses,
                                   PaybackStatistics simplePayback,
                                   PaybackStatistics discountedPayback,
                                   List<PeriodStatistics> cashFlows,
                                   List<MetricStatistics> operational) {

    public record NumericStatistics(int count, BigDecimal mean, BigDecimal min, BigDecimal max,
            BigDecimal p10, BigDecimal p50, BigDecimal p90) {}

    public record PaybackStatistics(int denominator, BigDecimal recoveredProbability,
            NumericStatistics recoveryPeriods, Map<String, Long> statuses, long becomesNegativeAgainCount) {}

    public record PeriodStatistics(int period, int year, NumericStatistics values) {}

    public record MetricStatistics(UUID nodeId, int period, String metric, NumericStatistics values) {}
}
