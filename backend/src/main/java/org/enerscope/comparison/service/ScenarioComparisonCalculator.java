package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import org.enerscope.comparison.dto.ComparisonResultDTO;
import org.enerscope.comparison.dto.ComparisonResultDTO.*;
import org.enerscope.comparison.dto.MetricValueDTO;
import org.enerscope.comparison.dto.NodeFlowDTO;
import org.enerscope.comparison.model.enums.*;
import org.springframework.stereotype.Component;

/**
 * Compares measured scenarios with each other. No scenario is a reference: for every
 * indicator each scenario gets its position among all of them (best, middle, worst)
 * and its distance to the best. Pure: no database, clock or security context.
 *
 * <p>The scenarios differ <em>relevantly</em> on an indicator when the gap between the
 * best and the worst value reaches:</p>
 * <ul>
 *   <li>{@code rateThreshold} (absolute, in fraction points) for rates (IRR, ROI, ROE);</li>
 *   <li>one year for paybacks;</li>
 *   <li>{@code relevanceThreshold} of the best value for anything else (any gap when the best is zero);</li>
 * </ul>
 * <p>and always when NPV is positive for some and not for others, or an indicator exists
 * for some scenarios only for a real reason (e.g. the investment is never recovered),
 * not for missing data. Amounts in different currencies are never ranked.</p>
 */
@Component
public class ScenarioComparisonCalculator {

    public static final BigDecimal DEFAULT_RELEVANCE_THRESHOLD = new BigDecimal("0.05");
    public static final BigDecimal DEFAULT_RATE_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal PAYBACK_THRESHOLD = BigDecimal.ONE;
    private static final BigDecimal UNBOUNDED = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");

    /** Findings with an ordering key: group first (sign split, availability, gaps, nodes), then size. */
    private record Ranked(int group, BigDecimal magnitude, int order, Finding finding) {}

    public ComparisonResultDTO compare(List<ScenarioMetrics> scenarios, BigDecimal relevanceThreshold,
                                       BigDecimal rateThreshold) {
        if (scenarios == null || scenarios.size() < 2) {
            throw new IllegalArgumentException("At least two scenarios are needed to compare");
        }
        BigDecimal relevance = relevanceThreshold == null ? DEFAULT_RELEVANCE_THRESHOLD : relevanceThreshold;
        BigDecimal rate = rateThreshold == null ? DEFAULT_RATE_THRESHOLD : rateThreshold;
        Map<UUID, String> names = displayNames(scenarios);
        boolean mixedCurrency = scenarios.stream().map(ScenarioMetrics::currency).distinct().count() > 1;
        List<Ranked> findings = new ArrayList<>();
        Map<UUID, List<ComparisonMetric>> bestIn = new LinkedHashMap<>();
        scenarios.forEach(s -> bestIn.put(s.evaluationId(), new ArrayList<>()));

        List<MetricRow> metrics = new ArrayList<>();
        for (ComparisonMetric metric : ComparisonMetric.values()) {
            MetricRow row = rankMetric(metric, scenarios, mixedCurrency, relevance, rate, names, findings);
            row.bestEvaluationIds().forEach(id -> bestIn.get(id).add(metric));
            metrics.add(row);
        }

        List<NodeRow> nodes = compareNodes(scenarios, relevance, names, findings);
        List<Series> annual = scenarios.stream().map(s -> new Series(s.evaluationId(), s.annual())).toList();
        List<Scenario> header = scenarios.stream().map(s -> new Scenario(s.evaluationId(), s.projectId(), s.projectName(),
                s.versionId(), s.versionName(), s.scenarioId(), s.scenarioName(), s.deleted(), s.evaluatedAt(), s.mode(), s.rounds(), s.currency(), s.startYear(),
                s.years(), s.wacc(), List.copyOf(bestIn.get(s.evaluationId())))).toList();

        findings.sort(Comparator.comparingInt(Ranked::group)
                .thenComparing(Ranked::magnitude, Comparator.reverseOrder())
                .thenComparingInt(Ranked::order));
        return new ComparisonResultDTO(relevance, rate, header, metrics, nodes, annual, warnings(scenarios),
                findings.stream().map(Ranked::finding).toList());
    }

    // ---------------------------------------------------------------- indicators

    private MetricRow rankMetric(ComparisonMetric metric, List<ScenarioMetrics> scenarios, boolean mixedCurrency,
                                 BigDecimal relevance, BigDecimal rate, Map<UUID, String> names, List<Ranked> findings) {
        List<UUID> ids = scenarios.stream().map(ScenarioMetrics::evaluationId).toList();
        List<MetricValueDTO> values = scenarios.stream().map(s -> s.metric(metric)).toList();
        List<Integer> available = new ArrayList<>();
        List<Integer> missingForAReason = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).isAvailable()) available.add(i);
            else if (!MetricValueDTO.NOT_AVAILABLE.equals(values.get(i).status())) missingForAReason.add(i);
        }
        boolean comparable = metric.unit() != MetricUnit.CURRENCY || !mixedCurrency;
        boolean relevant = false;

        // Some scenarios have the indicator, others cannot have it (e.g. never recover the investment).
        if (comparable && !available.isEmpty() && !missingForAReason.isEmpty()) {
            relevant = true;
            String others = available.size() == 1
                    ? join(available, ids, names) + ": " + format(metric, values.get(available.getFirst()).value())
                    : "los demás: " + range(metric, available.stream().map(i -> values.get(i).value()).toList());
            String message = String.format("%s: %s en %s (%s).", metric.label(),
                    status(values.get(missingForAReason.getFirst()).status()), join(missingForAReason, ids, names), others);
            findings.add(new Ranked(1, BigDecimal.ZERO, metric.ordinal(), new Finding(FindingType.AVAILABILITY_SPLIT,
                    missingForAReason.stream().map(ids::get).toList(), metric, null, null, message)));
        }

        if (!comparable) {
            List<MetricCell> cells = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) cells.add(new MetricCell(ids.get(i), values.get(i), null, null, null, Standing.NOT_COMPARABLE));
            return new MetricRow(metric, metric.label(), metric.category(), metric.unit(), metric.preference(),
                    List.of(), List.of(), false, cells);
        }

        if (available.size() < 2) {
            return new MetricRow(metric, metric.label(), metric.category(), metric.unit(), metric.preference(),
                    List.of(), List.of(), relevant, unranked(ids, values));
        }

        // Best first; for neutral indicators "best" just means the highest, and nothing is ranked.
        Comparator<BigDecimal> favorable = metric.preference() == MetricPreference.LOWER_IS_BETTER
                ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<Integer> ordered = new ArrayList<>(available);
        ordered.sort(Comparator.comparing(i -> values.get(i).value(), favorable));
        BigDecimal best = values.get(ordered.getFirst()).value();
        BigDecimal worst = values.get(ordered.getLast()).value();
        boolean tied = best.compareTo(worst) == 0;
        BigDecimal spread = best.subtract(worst).abs();
        BigDecimal spreadRelative = relative(spread, best);
        boolean gapRelevant = !tied && exceeds(metric.unit(), spread, spreadRelative, relevance, rate);
        List<Integer> bestOnes = available.stream().filter(i -> values.get(i).value().compareTo(best) == 0).toList();
        List<Integer> worstOnes = available.stream().filter(i -> values.get(i).value().compareTo(worst) == 0).toList();

        if (metric.preference() == MetricPreference.NEUTRAL) {
            if (gapRelevant) {
                // Neutral indicators sort highest first: "best" is the highest, "worst" the lowest, with no judgement.
                relevant = true;
                String message = String.format("%s: va de %s en %s a %s en %s.", metric.label(),
                        format(metric, worst), join(worstOnes, ids, names), format(metric, best), join(bestOnes, ids, names));
                findings.add(new Ranked(2, magnitude(spreadRelative), metric.ordinal(), new Finding(FindingType.LARGE_GAP,
                        concat(bestOnes, worstOnes).stream().map(ids::get).toList(), metric, null, spreadRelative, message)));
            }
            return new MetricRow(metric, metric.label(), metric.category(), metric.unit(), metric.preference(),
                    List.of(), List.of(), relevant, unranked(ids, values));
        }

        List<MetricCell> cells = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            MetricValueDTO value = values.get(i);
            if (!value.isAvailable()) {
                cells.add(new MetricCell(ids.get(i), value, null, null, null, Standing.UNRANKED));
                continue;
            }
            BigDecimal own = value.value();
            int rank = 1 + (int) available.stream().filter(j -> favorable.compare(values.get(j).value(), own) < 0).count();
            BigDecimal gap = own.subtract(best).abs();
            Standing standing = tied ? Standing.TIED
                    : own.compareTo(best) == 0 ? Standing.BEST
                    : own.compareTo(worst) == 0 ? Standing.WORST
                    : Standing.MIDDLE;
            cells.add(new MetricCell(ids.get(i), value, rank, gap, relative(gap, best), standing));
        }

        boolean signSplit = metric == ComparisonMetric.NPV
                && available.stream().anyMatch(i -> values.get(i).value().signum() > 0)
                && available.stream().anyMatch(i -> values.get(i).value().signum() <= 0);
        if (signSplit) {
            relevant = true;
            List<Integer> positive = available.stream().filter(i -> values.get(i).value().signum() > 0).toList();
            List<Integer> losing = available.stream().filter(i -> values.get(i).value().signum() <= 0).toList();
            String message = String.format("%s: %s %s valor y %s %s valor (%s).", metric.label(),
                    join(positive, ids, names), positive.size() == 1 ? "genera" : "generan",
                    join(losing, ids, names), losing.size() == 1 ? "pierde" : "pierden",
                    String.join("; ", losing.stream().map(i -> format(metric, values.get(i).value())).toList()));
            findings.add(new Ranked(0, magnitude(spreadRelative), metric.ordinal(), new Finding(FindingType.NPV_SIGN_SPLIT,
                    available.stream().map(ids::get).toList(), metric, null, spreadRelative, message)));
        } else if (gapRelevant) {
            relevant = true;
            String message = String.format("%s: %s %s el mejor (%s) y %s el peor (%s); %s de diferencia.", metric.label(),
                    join(bestOnes, ids, names), bestOnes.size() == 1 ? "es" : "son", format(metric, best),
                    join(worstOnes, ids, names), format(metric, worst), difference(metric.unit(), spread, spreadRelative));
            findings.add(new Ranked(2, magnitude(spreadRelative), metric.ordinal(), new Finding(FindingType.LARGE_GAP,
                    concat(bestOnes, worstOnes).stream().map(ids::get).toList(), metric, null, spreadRelative, message)));
        }

        return new MetricRow(metric, metric.label(), metric.category(), metric.unit(), metric.preference(),
                tied ? List.of() : bestOnes.stream().map(ids::get).toList(),
                tied ? List.of() : worstOnes.stream().map(ids::get).toList(), relevant, cells);
    }

    private static List<MetricCell> unranked(List<UUID> ids, List<MetricValueDTO> values) {
        List<MetricCell> cells = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) cells.add(new MetricCell(ids.get(i), values.get(i), null, null, null, Standing.UNRANKED));
        return cells;
    }

    private static boolean exceeds(MetricUnit unit, BigDecimal gap, BigDecimal relative, BigDecimal relevance, BigDecimal rate) {
        return switch (unit) {
            case FRACTION -> gap.compareTo(rate) >= 0;
            case YEARS -> gap.compareTo(PAYBACK_THRESHOLD) >= 0;
            default -> relative == null ? gap.signum() != 0 : relative.compareTo(relevance) >= 0;
        };
    }

    /** {@code gap / |reference|}, or {@code null} when the reference is zero. */
    private static BigDecimal relative(BigDecimal gap, BigDecimal reference) {
        if (reference == null || reference.signum() == 0) return null;
        return gap.divide(reference.abs(), MC).setScale(6, RoundingMode.HALF_UP);
    }

    /** Gaps from zero have no relative size; rank them above every finite one. */
    private static BigDecimal magnitude(BigDecimal relative) {
        return relative == null ? UNBOUNDED : relative.abs();
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        LinkedHashSet<Integer> all = new LinkedHashSet<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    // ---------------------------------------------------------------- nodes

    private List<NodeRow> compareNodes(List<ScenarioMetrics> scenarios, BigDecimal relevance, Map<UUID, String> names,
                                       List<Ranked> findings) {
        List<UUID> ids = scenarios.stream().map(ScenarioMetrics::evaluationId).toList();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        scenarios.forEach(s -> keys.addAll(s.nodes().keySet()));
        List<NodeRow> rows = new ArrayList<>();
        int order = 0;
        for (String key : keys) {
            order++;
            ScenarioMetrics.Node reference = scenarios.stream().map(s -> s.nodes().get(key)).filter(Objects::nonNull)
                    .findFirst().orElseThrow();
            String name = reference.name() == null ? key : reference.name();
            List<NodeCell> cells = new ArrayList<>();
            List<Integer> present = new ArrayList<>();
            for (int i = 0; i < scenarios.size(); i++) {
                ScenarioMetrics.Node node = scenarios.get(i).nodes().get(key);
                if (node != null) present.add(i);
                cells.add(new NodeCell(ids.get(i), node != null, node == null ? null : node.flows()));
            }
            boolean relevant = false;
            if (present.size() < scenarios.size()) {
                relevant = true;
                String message = String.format("Nodo «%s»: solo está en %s.", name, join(present, ids, names));
                findings.add(new Ranked(3, UNBOUNDED, order, new Finding(FindingType.NODE_PARTIAL,
                        present.stream().map(ids::get).toList(), null, key, null, message)));
            }
            Function<Integer, BigDecimal> output = i -> {
                NodeFlowDTO flows = cells.get(i).flows();
                return flows == null ? null : flows.output();
            };
            List<Integer> measured = present.stream().filter(i -> output.apply(i) != null).toList();
            if (measured.size() >= 2) {
                int high = measured.stream().max(Comparator.comparing(output)).orElseThrow();
                int low = measured.stream().min(Comparator.comparing(output)).orElseThrow();
                BigDecimal spread = output.apply(high).subtract(output.apply(low));
                BigDecimal spreadRelative = relative(spread, output.apply(high));
                if (spread.signum() != 0 && (spreadRelative == null || spreadRelative.compareTo(relevance) >= 0)) {
                    relevant = true;
                    String message = String.format("Nodo «%s»: la salida va de %s en %s a %s en %s.", name,
                            number(output.apply(low), 2), join(List.of(low), ids, names),
                            number(output.apply(high), 2), join(List.of(high), ids, names));
                    findings.add(new Ranked(3, magnitude(spreadRelative), order, new Finding(FindingType.NODE_FLOW_GAP,
                            List.of(ids.get(low), ids.get(high)), null, key, spreadRelative, message)));
                }
            }
            rows.add(new NodeRow(key, name, reference.nodeType(), relevant, cells));
        }
        return rows;
    }

    // ---------------------------------------------------------------- warnings

    private static List<Warning> warnings(List<ScenarioMetrics> scenarios) {
        List<Warning> warnings = new ArrayList<>();
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_CURRENCY, s -> String.valueOf(s.currency()));
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_START_YEAR, s -> String.valueOf(s.startYear()));
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_HORIZON, s -> String.valueOf(s.years()));
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_WACC,
                s -> s.wacc() == null ? "null" : s.wacc().stripTrailingZeros().toPlainString());
        differs(warnings, scenarios, ComparabilityWarning.MIXED_EVALUATION_MODES, s -> String.valueOf(s.mode()));
        List<ScenarioMetrics> withVersion = scenarios.stream().filter(s -> s.simulationAlgorithm() != null).toList();
        if (withVersion.stream().map(s -> s.simulationAlgorithm() + "/" + s.simulationCalculationVersion()).distinct().count() > 1) {
            warnings.add(new Warning(ComparabilityWarning.DIFFERENT_SIMULATION_VERSION,
                    ComparabilityWarning.DIFFERENT_SIMULATION_VERSION.message(),
                    withVersion.stream().map(ScenarioMetrics::evaluationId).toList()));
        }
        List<UUID> incomplete = scenarios.stream().filter(ScenarioMetrics::incomplete).map(ScenarioMetrics::evaluationId).toList();
        if (!incomplete.isEmpty()) {
            warnings.add(new Warning(ComparabilityWarning.INCOMPLETE_RESULTS,
                    ComparabilityWarning.INCOMPLETE_RESULTS.message(), incomplete));
        }
        return warnings;
    }

    /** One warning when the scenarios do not all share a setting; it names every scenario. */
    private static void differs(List<Warning> warnings, List<ScenarioMetrics> scenarios, ComparabilityWarning code,
                                Function<ScenarioMetrics, String> setting) {
        if (scenarios.stream().map(setting).distinct().count() > 1) {
            warnings.add(new Warning(code, code.message(), scenarios.stream().map(ScenarioMetrics::evaluationId).toList()));
        }
    }

    // ---------------------------------------------------------------- text

    /**
     * The names the findings use: the scenario name when it is distinct among the compared scenarios, otherwise
     * "version · scenario" (the default scenarios of different versions all share a name), and disambiguated by position
     * when two evaluations of the same scenario are compared. Without a scenario name (older data) the version name stands.
     */
    private static Map<UUID, String> displayNames(List<ScenarioMetrics> scenarios) {
        Map<String, Long> scenarioNameCounts = new HashMap<>();
        scenarios.forEach(s -> scenarioNameCounts.merge(String.valueOf(s.scenarioName()), 1L, Long::sum));
        List<String> base = scenarios.stream().map(s -> {
            if (s.scenarioName() == null) return String.valueOf(s.versionName());
            return scenarioNameCounts.get(s.scenarioName()) > 1 ? s.versionName() + " · " + s.scenarioName() : s.scenarioName();
        }).toList();
        Map<String, Long> counts = new HashMap<>();
        base.forEach(name -> counts.merge(name, 1L, Long::sum));
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < scenarios.size(); i++) {
            String name = base.get(i);
            names.put(scenarios.get(i).evaluationId(), counts.get(name) > 1 ? name + " #" + (i + 1) : name);
        }
        return names;
    }

    /** «A», «A» y «B», «A», «B» y «C». */
    private static String join(List<Integer> indexes, List<UUID> ids, Map<UUID, String> names) {
        List<String> quoted = indexes.stream().map(i -> "«" + names.get(ids.get(i)) + "»").toList();
        if (quoted.size() <= 1) return String.join("", quoted);
        return String.join(", ", quoted.subList(0, quoted.size() - 1)) + " y " + quoted.getLast();
    }

    private static String range(ComparisonMetric metric, List<BigDecimal> values) {
        BigDecimal min = values.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = values.stream().max(Comparator.naturalOrder()).orElseThrow();
        return min.compareTo(max) == 0 ? format(metric, min) : "entre " + format(metric, min) + " y " + format(metric, max);
    }

    private static String difference(MetricUnit unit, BigDecimal gap, BigDecimal relative) {
        return switch (unit) {
            case FRACTION -> number(gap.movePointRight(2), 2) + " p.p.";
            case YEARS -> number(gap, 0) + (gap.compareTo(BigDecimal.ONE) == 0 ? " año" : " años");
            default -> number(gap, 2) + (relative == null ? "" : " (" + number(relative.movePointRight(2), 0) + " % del mejor)");
        };
    }

    private static String format(ComparisonMetric metric, BigDecimal value) {
        return switch (metric.unit()) {
            case FRACTION -> number(value.movePointRight(2), 2) + " %";
            case YEARS -> number(value, 1) + " años";
            default -> number(value, 2);
        };
    }

    private static String number(BigDecimal value, int decimals) {
        return String.format(SPANISH, "%,." + decimals + "f", value);
    }

    private static String status(String status) {
        return switch (status) {
            case "NOT_RECOVERED_WITHIN_HORIZON" -> "no se recupera dentro del horizonte";
            case "NOT_APPLICABLE" -> "no aplica";
            case ScenarioMetricsExtractor.NO_INVESTMENT -> "sin inversión";
            case "NO_SIGN_CHANGE" -> "el flujo no cambia de signo";
            case "NON_CONVENTIONAL" -> "flujo no convencional";
            case "NUMERICAL_FAILURE" -> "falla numérica";
            case "INSUFFICIENT_DATA" -> "datos insuficientes";
            default -> "sin datos";
        };
    }
}
