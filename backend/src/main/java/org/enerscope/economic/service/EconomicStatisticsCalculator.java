package org.enerscope.economic.service;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.enerscope.economic.model.enums.*;
import org.enerscope.economic.model.results.*;
import org.enerscope.economic.model.results.ProbabilisticSummary.*;
import org.enerscope.simulator.AnnualNodeMetrics;
import org.springframework.stereotype.Service;

@Service
public class EconomicStatisticsCalculator {
    private static final MathContext MC = MathContext.DECIMAL128;
    public NumericStatistics statistics(List<BigDecimal> input) {
        List<BigDecimal> sorted = input.stream().filter(Objects::nonNull).sorted().toList();
        int n = sorted.size();
        if (n == 0) return new NumericStatistics(0, null, null, null, null, null, null);
        BigDecimal mean = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(n), MC);
        return new NumericStatistics(n, mean, sorted.getFirst(), sorted.getLast(),
                percentile(sorted, 10), percentile(sorted, 50), percentile(sorted, 90));
    }

    private BigDecimal percentile(List<BigDecimal> values, int p) {
        return values.get((p * values.size() + 99) / 100 - 1);
    }

    private BigDecimal probability(long count, int denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(count).divide(BigDecimal.valueOf(denominator), MC);
    }

    public ProbabilisticSummary calculate(List<EconomicRoundResult> rounds) {
        if (rounds == null || rounds.isEmpty()) throw new IllegalArgumentException("At least one completed round required");
        var results = rounds.stream().map(EconomicRoundResult::result).toList();
        var npvs = results.stream().map(EconomicResult::npv).toList();
        var irrs = results.stream().map(r -> r.indicators().irr()).toList();
        List<PeriodStatistics> cash = new ArrayList<>();
        for (var period : results.getFirst().periods()) {
            List<BigDecimal> values = new ArrayList<>();
            for (var result : results) {
                if (result.periods().size() != results.getFirst().periods().size())
                    throw new IllegalArgumentException("Round horizons differ");
                var p = result.periods().get(period.period());
                if (p.period() != period.period() || p.year() != period.year())
                    throw new IllegalArgumentException("Round periods differ");
                values.add(p.cashFlow());
            }
            cash.add(new PeriodStatistics(period.period(), period.year(), statistics(values)));
        }
        record Key(UUID node, int period, String metric) {}
        Map<Key,List<BigDecimal>> metrics = new LinkedHashMap<>();
        for (var round : rounds) for (var metric : round.rawMetrics()) {
            add(metrics, new Key(metric.nodeId(), metric.period(), "input"), metric.input());
            add(metrics, new Key(metric.nodeId(), metric.period(), "output"), metric.output());
            add(metrics, new Key(metric.nodeId(), metric.period(), "exported"), metric.exported());
            add(metrics, new Key(metric.nodeId(), metric.period(), "losses"), metric.losses());
            add(metrics, new Key(metric.nodeId(), metric.period(), "operatingHours"), metric.operatingHours());
            add(metrics, new Key(metric.nodeId(), metric.period(), "events"), metric.events());
        }
        List<MetricStatistics> operational = new ArrayList<>();
        metrics.forEach((key, values) -> {
            if (values.size() != rounds.size()) throw new IllegalArgumentException("Incomplete round metrics");
            operational.add(new MetricStatistics(key.node(), key.period(), key.metric(), statistics(values)));
        });
        operational.sort(Comparator.comparing((MetricStatistics m) -> m.nodeId().toString())
                .thenComparingInt(MetricStatistics::period).thenComparing(MetricStatistics::metric));
        return new ProbabilisticSummary(rounds.size(), statistics(npvs),
                probability(npvs.stream().filter(v -> v.signum() > 0).count(), rounds.size()),
                statistics(irrs.stream().filter(i -> i.status() == IrrStatus.CALCULATED).map(IrrResult::rate).toList()),
                counts(irrs, i -> i.status().name()),
                paybacks(results.stream().map(r -> r.indicators().simplePayback()).toList()),
                paybacks(results.stream().map(r -> r.indicators().discountedPayback()).toList()), cash, operational);
    }

    private <K> void add(Map<K,List<BigDecimal>> map, K key, BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Missing operational quantity");
        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }

    private <T> Map<String,Long> counts(List<T> values, Function<T,String> status) {
        return values.stream().collect(Collectors.groupingBy(status, TreeMap::new, Collectors.counting()));
    }

    private PaybackStatistics paybacks(List<PaybackResult> values) {
        var recovered = values.stream().filter(v -> v.status() == PaybackStatus.RECOVERED).toList();
        int denominator = (int) values.stream().filter(v -> v.status() == PaybackStatus.RECOVERED
                || v.status() == PaybackStatus.NOT_RECOVERED_WITHIN_HORIZON).count();
        return new PaybackStatistics(denominator, probability(recovered.size(), denominator),
                statistics(recovered.stream().map(v -> BigDecimal.valueOf(v.period())).toList()),
                counts(values, v -> v.status().name()), recovered.stream().filter(PaybackResult::becomesNegativeAgain).count());
    }
}
