package org.enerscope.comparison.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.function.Function;
import org.enerscope.comparison.dto.ComparisonResultDTO.SeriesPoint;
import org.enerscope.comparison.dto.MetricValueDTO;
import org.enerscope.comparison.dto.NodeFlowDTO;
import org.enerscope.comparison.model.enums.ComparisonMetric;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.economic.dto.EvaluationDTO.Snapshot;
import org.enerscope.economic.model.EconomicEntry;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.Direction;
import org.enerscope.economic.model.enums.CashClassification;
import org.enerscope.economic.model.enums.IrrStatus;
import org.enerscope.economic.model.enums.PaybackStatus;
import org.enerscope.economic.model.enums.TaxClassification;
import org.enerscope.economic.model.results.EconomicIndicators;
import org.enerscope.economic.model.results.EconomicResult;
import org.enerscope.economic.model.results.PaybackResult;
import org.enerscope.economic.model.results.PeriodEconomicResult;
import org.enerscope.economic.service.EconomicStatisticsCalculator;
import org.enerscope.simulator.AnnualNodeMetrics;
import org.springframework.stereotype.Component;

import static org.enerscope.comparison.model.enums.ComparisonMetric.*;

/**
 * Measures one saved evaluation: financial indicators, costs, cash totals and
 * physical flows. Pure — reads only the snapshot (and saved rounds) it is given,
 * never the current scenario configuration.
 *
 * <p>A deterministic evaluation is one realization. A probabilistic one is measured
 * round by round and every indicator is the mean over the rounds where it exists;
 * the count of those rounds travels with the value so conditional means stay
 * explicit. A missing value is never replaced by zero.</p>
 *
 * <p>Definitions (all undiscounted unless stated, over the evaluation horizon and
 * inside its consolidation boundary, like the economic engine):</p>
 * <ul>
 *   <li>CAPEX: capitalizable expense entries (asset purchases).</li>
 *   <li>OPEX: other cash expense entries. Excludes depreciation (non-cash) and income tax.</li>
 *   <li>Revenue: cash income entries.</li>
 *   <li>ROI: net cash flow over the horizon / CAPEX.</li>
 *   <li>ROE: average annual result after tax / CAPEX. The model has no debt financing,
 *       so the invested capital is the equity.</li>
 *   <li>Produced volume: output of the source nodes (nodes no connection feeds), so a
 *       serial chain is not counted twice. Exported volume and losses add over all nodes.</li>
 * </ul>
 */
@Component
public class ScenarioMetricsExtractor {

    public static final String NO_INVESTMENT = "NO_INVESTMENT";
    private static final MathContext MC = MathContext.DECIMAL128;

    private final EconomicStatisticsCalculator statistics;

    public ScenarioMetricsExtractor(EconomicStatisticsCalculator statistics) {
        this.statistics = statistics;
    }

    private record Realization(EconomicResult result, List<AnnualNodeMetrics> raw) {}

    private record Outcome(BigDecimal value, String status) {
        static Outcome of(BigDecimal value) {
            return value == null ? missing() : new Outcome(value, MetricValueDTO.AVAILABLE);
        }
        static Outcome missing() {
            return new Outcome(null, MetricValueDTO.NOT_AVAILABLE);
        }
        static Outcome status(String status) {
            return new Outcome(null, status);
        }
    }

    private record NodeInfo(String key, String name, String nodeType) {}

    public ScenarioMetrics extract(ScenarioSource source) {
        Snapshot snapshot = Objects.requireNonNull(source.snapshot(), "Evaluation snapshot is required");
        EconomicConfiguration configuration = snapshot.configuration();
        boolean probabilistic = snapshot.schemaVersion() >= 3 || snapshot.summary() != null;
        List<Realization> realizations = probabilistic
                ? source.rounds().stream().map(round -> new Realization(round.result(), round.rawMetrics())).toList()
                : List.of(new Realization(snapshot.result(), snapshot.rawMetrics()));

        Map<UUID, NodeInfo> nodes = nodeInfo(snapshot.physicalNodes());
        Set<UUID> sources = sourceNodes(nodes.keySet(), snapshot.physicalConnections());

        List<Map<ComparisonMetric, Outcome>> measured = realizations.stream()
                .map(realization -> measure(realization, configuration, sources)).toList();
        Map<ComparisonMetric, MetricValueDTO> metrics = new EnumMap<>(ComparisonMetric.class);
        for (ComparisonMetric metric : ComparisonMetric.values()) {
            metrics.put(metric, aggregate(measured.stream().map(m -> m.get(metric)).toList(), probabilistic));
        }

        var simulation = snapshot.simulation();
        return new ScenarioMetrics(source.evaluationId(), source.versionId(), source.versionName(), source.evaluatedAt(),
                probabilistic ? EvaluationMode.PROBABILISTIC : EvaluationMode.DETERMINISTIC, realizations.size(),
                configuration == null ? null : configuration.currency(),
                configuration == null ? null : configuration.startYear(),
                configuration == null ? null : configuration.years(),
                configuration == null ? null : configuration.wacc(),
                simulation == null ? null : simulation.algorithm(),
                simulation == null ? null : simulation.calculationVersion(),
                metrics, nodeFlows(nodes, realizations), annual(realizations, configuration, sources));
    }

    // ---------------------------------------------------------------- per realization

    private Map<ComparisonMetric, Outcome> measure(Realization realization, EconomicConfiguration configuration,
                                                   Set<UUID> sources) {
        Map<ComparisonMetric, Outcome> out = new EnumMap<>(ComparisonMetric.class);
        EconomicResult result = realization.result();
        EconomicIndicators indicators = result == null ? null : result.indicators();

        out.put(NPV, Outcome.of(result == null ? null : result.npv()));
        out.put(IRR, indicators == null || indicators.irr() == null ? Outcome.missing()
                : indicators.irr().status() == IrrStatus.CALCULATED ? Outcome.of(indicators.irr().rate())
                : Outcome.status(indicators.irr().status().name()));
        out.put(SIMPLE_PAYBACK, payback(indicators == null ? null : indicators.simplePayback()));
        out.put(DISCOUNTED_PAYBACK, payback(indicators == null ? null : indicators.discountedPayback()));

        List<EconomicEntry> entries = result == null || result.entries() == null ? null
                : result.entries().stream().filter(e -> counted(configuration, e)).toList();
        Outcome capex = entries == null ? Outcome.missing() : Outcome.of(sum(entries.stream()
                .filter(e -> e.direction() == Direction.EXPENSE && e.taxClassification() == TaxClassification.CAPITALIZABLE)
                .map(EconomicEntry::amount).toList()));
        out.put(CAPEX, capex);
        out.put(OPEX, entries == null ? Outcome.missing() : Outcome.of(sum(entries.stream()
                .filter(e -> e.direction() == Direction.EXPENSE && e.cashClassification() == CashClassification.CASH
                        && e.taxClassification() != TaxClassification.CAPITALIZABLE)
                .map(EconomicEntry::amount).toList())));
        out.put(REVENUE, entries == null ? Outcome.missing() : Outcome.of(sum(entries.stream()
                .filter(e -> e.direction() == Direction.INCOME && e.cashClassification() == CashClassification.CASH)
                .map(EconomicEntry::amount).toList())));

        List<PeriodEconomicResult> periods = result == null || result.periods() == null || result.periods().isEmpty()
                ? null : result.periods();
        Outcome taxes = periods == null ? Outcome.missing() : Outcome.of(sum(periods.stream().map(PeriodEconomicResult::taxes).toList()));
        Outcome cash = periods == null ? Outcome.missing() : Outcome.of(sum(periods.stream().map(PeriodEconomicResult::cashFlow).toList()));
        Outcome afterTax = periods == null ? Outcome.missing() : Outcome.of(sum(periods.stream().map(PeriodEconomicResult::resultAfterTax).toList()));
        out.put(TAXES, taxes);
        out.put(NET_CASH_FLOW, cash);
        out.put(ROI, overInvestment(cash, capex, BigDecimal.ONE));
        out.put(ROE, overInvestment(afterTax, capex, periods == null ? null : BigDecimal.valueOf(periods.size())));

        List<AnnualNodeMetrics> raw = realization.raw() == null || realization.raw().isEmpty() ? null : realization.raw();
        out.put(PRODUCED_VOLUME, raw == null || sources == null ? Outcome.missing() : Outcome.of(sum(raw.stream()
                .filter(m -> sources.contains(m.nodeId())).map(AnnualNodeMetrics::output).toList())));
        out.put(EXPORTED_VOLUME, physical(raw, AnnualNodeMetrics::exported));
        out.put(LOSSES, physical(raw, AnnualNodeMetrics::losses));
        out.put(OPERATING_HOURS, physical(raw, AnnualNodeMetrics::operatingHours));
        out.put(EVENTS, physical(raw, AnnualNodeMetrics::events));
        return out;
    }

    private static Outcome payback(PaybackResult payback) {
        if (payback == null || payback.status() == null) return Outcome.missing();
        if (payback.status() != PaybackStatus.RECOVERED) return Outcome.status(payback.status().name());
        return payback.period() == null ? Outcome.missing() : Outcome.of(BigDecimal.valueOf(payback.period()));
    }

    /** {@code numerator / periods / CAPEX}; no investment means the ratio does not exist. */
    private static Outcome overInvestment(Outcome numerator, Outcome capex, BigDecimal periods) {
        if (numerator.value() == null || capex.value() == null || periods == null) return Outcome.missing();
        if (capex.value().signum() == 0) return Outcome.status(NO_INVESTMENT);
        return Outcome.of(numerator.value().divide(periods, MC).divide(capex.value(), MC));
    }

    private static Outcome physical(List<AnnualNodeMetrics> raw, Function<AnnualNodeMetrics, BigDecimal> field) {
        return raw == null ? Outcome.missing() : Outcome.of(sum(raw.stream().map(field).toList()));
    }

    /** Same consolidation rule as the economic engine, restricted to the evaluation horizon. */
    private static boolean counted(EconomicConfiguration configuration, EconomicEntry entry) {
        if (entry == null || entry.amount() == null) return false;
        if (configuration == null) return true;
        Set<String> boundary = configuration.boundary();
        boolean inside = boundary == null || (boundary.contains(entry.taxEntityId())
                && (entry.counterpartyId() == null || !boundary.contains(entry.counterpartyId())));
        int year = entry.recognitionDate() == null ? configuration.startYear() : entry.recognitionDate().getYear();
        return inside && year >= configuration.startYear() && year <= configuration.startYear() + configuration.years();
    }

    /** Null as soon as one term is missing: a partial sum would read as a real total. */
    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value == null) return null;
            total = total.add(value);
        }
        return total;
    }

    // ---------------------------------------------------------------- aggregation

    private MetricValueDTO aggregate(List<Outcome> outcomes, boolean probabilistic) {
        int total = outcomes.size();
        if (total == 0) return new MetricValueDTO(null, MetricValueDTO.NOT_AVAILABLE, 0, 0, null);
        List<BigDecimal> values = outcomes.stream().map(Outcome::value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) return new MetricValueDTO(null, mostFrequentStatus(outcomes), 0, total, null);
        if (!probabilistic) return new MetricValueDTO(values.getFirst(), MetricValueDTO.AVAILABLE, 1, total, null);
        var distribution = statistics.statistics(values);
        return new MetricValueDTO(distribution.mean(), MetricValueDTO.AVAILABLE, values.size(), total, distribution);
    }

    private static String mostFrequentStatus(List<Outcome> outcomes) {
        Map<String, Long> counts = new TreeMap<>();
        outcomes.forEach(outcome -> counts.merge(outcome.status(), 1L, Long::sum));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(present.size()), MC);
    }

    // ---------------------------------------------------------------- nodes

    private static Map<UUID, NodeInfo> nodeInfo(JsonNode physicalNodes) {
        Map<UUID, NodeInfo> nodes = new LinkedHashMap<>();
        if (physicalNodes == null || !physicalNodes.isArray()) return nodes;
        for (JsonNode node : physicalNodes) {
            UUID id = uuid(node.path("id"));
            if (id == null) continue;
            UUID identity = uuid(node.path("identityId"));
            JsonNode type = node.path("type").path("nodeType");
            nodes.put(id, new NodeInfo((identity == null ? id : identity).toString(),
                    node.path("name").isTextual() ? node.path("name").asText() : null,
                    type.isTextual() ? type.asText() : null));
        }
        return nodes;
    }

    /** Nodes no connection feeds; {@code null} when the snapshot did not save its connections. */
    private static Set<UUID> sourceNodes(Set<UUID> nodes, JsonNode connections) {
        if (connections == null || !connections.isArray()) return null;
        Set<UUID> fed = new HashSet<>();
        for (JsonNode connection : connections) {
            UUID from = uuid(connection.path("fromNodeId"));
            UUID to = uuid(connection.path("toNodeId"));
            if (from != null && to != null && !from.equals(to)) fed.add(to);
        }
        Set<UUID> sources = new HashSet<>(nodes);
        sources.removeAll(fed);
        return sources;
    }

    private static UUID uuid(JsonNode value) {
        if (value == null || !value.isTextual()) return null;
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Map<String, ScenarioMetrics.Node> nodeFlows(Map<UUID, NodeInfo> info, List<Realization> realizations) {
        // node id -> field index -> one total per realization
        Map<UUID, List<List<BigDecimal>>> totals = new LinkedHashMap<>();
        info.keySet().forEach(id -> totals.put(id, null));
        for (int r = 0; r < realizations.size(); r++) {
            List<AnnualNodeMetrics> raw = realizations.get(r).raw();
            if (raw == null) continue;
            Map<UUID, BigDecimal[]> sums = new LinkedHashMap<>();
            for (AnnualNodeMetrics metric : raw) {
                BigDecimal[] sum = sums.computeIfAbsent(metric.nodeId(), ignored -> zeros());
                BigDecimal[] values = {metric.input(), metric.output(), metric.exported(), metric.losses(),
                        metric.operatingHours(), metric.events()};
                for (int i = 0; i < values.length; i++) {
                    sum[i] = sum[i] == null || values[i] == null ? null : sum[i].add(values[i]);
                }
            }
            sums.forEach((id, sum) -> {
                List<List<BigDecimal>> fields = totals.get(id);
                if (fields == null) {
                    fields = new ArrayList<>();
                    for (int i = 0; i < 6; i++) fields.add(new ArrayList<>());
                    totals.put(id, fields);
                }
                for (int i = 0; i < 6; i++) fields.get(i).add(sum[i]);
            });
        }
        Map<String, ScenarioMetrics.Node> nodes = new LinkedHashMap<>();
        totals.forEach((id, fields) -> {
            NodeInfo node = info.getOrDefault(id, new NodeInfo(id.toString(), null, null));
            NodeFlowDTO flows = fields == null ? null : new NodeFlowDTO(mean(fields.get(0)), mean(fields.get(1)),
                    mean(fields.get(2)), mean(fields.get(3)), mean(fields.get(4)), mean(fields.get(5)));
            nodes.put(node.key(), new ScenarioMetrics.Node(node.key(), node.name(), node.nodeType(), flows));
        });
        return nodes;
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] values = new BigDecimal[6];
        Arrays.fill(values, BigDecimal.ZERO);
        return values;
    }

    // ---------------------------------------------------------------- annual series

    private static List<SeriesPoint> annual(List<Realization> realizations, EconomicConfiguration configuration,
                                            Set<UUID> sources) {
        int offset = configuration == null ? 0 : configuration.startYear();
        SortedMap<Integer, List<BigDecimal>> cash = new TreeMap<>();
        SortedMap<Integer, List<BigDecimal>> produced = new TreeMap<>();
        SortedMap<Integer, List<BigDecimal>> exported = new TreeMap<>();
        if (configuration != null) {
            for (int t = 0; t <= configuration.years(); t++) cash.put(offset + t, new ArrayList<>());
        }
        for (Realization realization : realizations) {
            EconomicResult result = realization.result();
            if (result != null && result.periods() != null) {
                for (PeriodEconomicResult period : result.periods()) {
                    cash.computeIfAbsent(period.year(), ignored -> new ArrayList<>()).add(period.cashFlow());
                }
            }
            if (realization.raw() == null) continue;
            Map<Integer, BigDecimal> producedThisRound = new TreeMap<>();
            Map<Integer, BigDecimal> exportedThisRound = new TreeMap<>();
            for (AnnualNodeMetrics metric : realization.raw()) {
                int year = offset + metric.period();
                if (sources != null && sources.contains(metric.nodeId()) && metric.output() != null) {
                    producedThisRound.merge(year, metric.output(), BigDecimal::add);
                }
                if (metric.exported() != null) exportedThisRound.merge(year, metric.exported(), BigDecimal::add);
            }
            producedThisRound.forEach((year, value) -> produced.computeIfAbsent(year, ignored -> new ArrayList<>()).add(value));
            exportedThisRound.forEach((year, value) -> exported.computeIfAbsent(year, ignored -> new ArrayList<>()).add(value));
        }
        SortedSet<Integer> years = new TreeSet<>(cash.keySet());
        years.addAll(produced.keySet());
        years.addAll(exported.keySet());
        List<SeriesPoint> points = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int year : years) {
            BigDecimal flow = mean(cash.getOrDefault(year, List.of()));
            cumulative = cumulative == null || flow == null ? null : cumulative.add(flow);
            points.add(new SeriesPoint(year, flow, cumulative,
                    mean(produced.getOrDefault(year, List.of())), mean(exported.getOrDefault(year, List.of()))));
        }
        return points;
    }
}
