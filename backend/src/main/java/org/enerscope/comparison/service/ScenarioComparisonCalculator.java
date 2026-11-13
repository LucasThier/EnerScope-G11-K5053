package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import org.enerscope.comparison.dto.ComparisonResultDTO;
import org.enerscope.comparison.dto.ComparisonResultDTO.*;
import org.enerscope.comparison.dto.MetricValueDTO;
import org.enerscope.comparison.dto.NodeFlowDTO;
import org.enerscope.comparison.model.enums.*;
import org.springframework.stereotype.Component;

import static org.enerscope.comparison.model.enums.DifferenceAssessment.*;

/**
 * Puts measured scenarios side by side and flags what differs. Pure: no
 * database, clock or security context. The first scenario is the baseline.
 *
 * <p>A difference against the baseline is <em>relevant</em> when:</p>
 * <ul>
 *   <li>rates (IRR, ROI, ROE) move at least {@code rateThreshold} (absolute, in fraction points);</li>
 *   <li>paybacks move at least one year;</li>
 *   <li>any other value moves at least {@code relevanceThreshold} relative to the baseline
 *       (any change counts when the baseline is zero);</li>
 *   <li>NPV changes sign, or an indicator exists on one side only for a real reason
 *       (e.g. the investment is never recovered), not for missing data.</li>
 * </ul>
 * <p>Amounts in a different currency than the baseline's are never compared.</p>
 */
@Component
public class ScenarioComparisonCalculator {

    public static final BigDecimal DEFAULT_RELEVANCE_THRESHOLD = new BigDecimal("0.05");
    public static final BigDecimal DEFAULT_RATE_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal PAYBACK_THRESHOLD = BigDecimal.ONE;
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");

    /** Findings with an ordering key: group first, then magnitude. */
    private record Ranked(int group, BigDecimal magnitude, int scenario, Finding finding) {}

    public ComparisonResultDTO compare(List<ScenarioMetrics> scenarios, BigDecimal relevanceThreshold,
                                       BigDecimal rateThreshold) {
        if (scenarios == null || scenarios.size() < 2) {
            throw new IllegalArgumentException("At least two scenarios are needed to compare");
        }
        BigDecimal relevance = relevanceThreshold == null ? DEFAULT_RELEVANCE_THRESHOLD : relevanceThreshold;
        BigDecimal rate = rateThreshold == null ? DEFAULT_RATE_THRESHOLD : rateThreshold;
        ScenarioMetrics baseline = scenarios.getFirst();
        Map<UUID, String> names = displayNames(scenarios);
        List<Ranked> findings = new ArrayList<>();
        Map<UUID, List<ComparisonMetric>> bestIn = new LinkedHashMap<>();
        scenarios.forEach(s -> bestIn.put(s.evaluationId(), new ArrayList<>()));

        List<MetricRow> metrics = new ArrayList<>();
        for (ComparisonMetric metric : ComparisonMetric.values()) {
            List<MetricCell> cells = new ArrayList<>();
            for (int i = 0; i < scenarios.size(); i++) {
                ScenarioMetrics scenario = scenarios.get(i);
                if (i == 0) {
                    cells.add(new MetricCell(scenario.evaluationId(), scenario.metric(metric), null, null, BASELINE, false));
                } else {
                    cells.add(compareMetric(metric, baseline, scenario, i, relevance, rate, names, findings));
                }
            }
            List<UUID> best = best(metric, baseline, scenarios);
            best.forEach(id -> bestIn.get(id).add(metric));
            metrics.add(new MetricRow(metric, metric.label(), metric.category(), metric.unit(), metric.preference(),
                    best, cells.stream().anyMatch(MetricCell::relevant), cells));
        }

        List<NodeRow> nodes = compareNodes(scenarios, relevance, names, findings);
        List<Series> annual = scenarios.stream().map(s -> new Series(s.evaluationId(), s.annual())).toList();
        List<Scenario> header = scenarios.stream().map(s -> new Scenario(s.evaluationId(), s.versionId(),
                s.versionName(), s.evaluatedAt(), s == baseline, s.mode(), s.rounds(), s.currency(), s.startYear(),
                s.years(), s.wacc(), List.copyOf(bestIn.get(s.evaluationId())))).toList();

        findings.sort(Comparator.comparingInt(Ranked::group)
                .thenComparing(Ranked::magnitude, Comparator.reverseOrder())
                .thenComparingInt(Ranked::scenario));
        return new ComparisonResultDTO(baseline.evaluationId(), relevance, rate, header, metrics, nodes, annual,
                warnings(scenarios), findings.stream().map(Ranked::finding).toList());
    }

    // ---------------------------------------------------------------- indicators

    private MetricCell compareMetric(ComparisonMetric metric, ScenarioMetrics baseline, ScenarioMetrics scenario,
                                     int position, BigDecimal relevance, BigDecimal rate, Map<UUID, String> names,
                                     List<Ranked> findings) {
        MetricValueDTO base = baseline.metric(metric);
        MetricValueDTO value = scenario.metric(metric);
        UUID id = scenario.evaluationId();
        if (metric.unit() == MetricUnit.CURRENCY && !Objects.equals(baseline.currency(), scenario.currency())) {
            return new MetricCell(id, value, null, null, NOT_COMPARABLE, false);
        }
        if (!base.isAvailable() || !value.isAvailable()) {
            if (!base.isAvailable() && !value.isAvailable()) {
                return new MetricCell(id, value, null, null,
                        Objects.equals(base.status(), value.status()) ? EQUAL : NOT_COMPARABLE, false);
            }
            boolean missingData = MetricValueDTO.NOT_AVAILABLE.equals(base.status())
                    || MetricValueDTO.NOT_AVAILABLE.equals(value.status());
            DifferenceAssessment assessment = availabilityAssessment(metric, base, value);
            if (!missingData) {
                String message = base.isAvailable()
                        ? String.format("%s: disponible en la línea base «%s» (%s) pero no en «%s» (%s).", metric.label(),
                                names.get(baseline.evaluationId()), format(metric, base.value()), names.get(id), status(value.status()))
                        : String.format("%s: no disponible en la línea base «%s» (%s) pero sí en «%s» (%s).", metric.label(),
                                names.get(baseline.evaluationId()), status(base.status()), names.get(id), format(metric, value.value()));
                findings.add(new Ranked(1, BigDecimal.ZERO, position,
                        new Finding(FindingType.AVAILABILITY_CHANGE, id, metric, null, null, message)));
            }
            return new MetricCell(id, value, null, null, assessment, !missingData);
        }

        BigDecimal delta = value.value().subtract(base.value());
        BigDecimal relative = relative(delta, base.value());
        DifferenceAssessment assessment = assess(metric.preference(), delta);
        boolean signChange = metric == ComparisonMetric.NPV && (base.value().signum() > 0) != (value.value().signum() > 0);
        boolean relevant = assessment != EQUAL && (signChange || exceeds(metric.unit(), delta, relative, relevance, rate));
        if (signChange) {
            String message = String.format("%s: «%s» pasa a %s respecto de la línea base «%s» (%s → %s).", metric.label(),
                    names.get(id), value.value().signum() > 0 ? "positivo" : "no positivo",
                    names.get(baseline.evaluationId()), format(metric, base.value()), format(metric, value.value()));
            findings.add(new Ranked(0, magnitude(relative), position,
                    new Finding(FindingType.NPV_SIGN_CHANGE, id, metric, null, relative, message)));
        } else if (relevant) {
            FindingType type = switch (assessment) {
                case BETTER -> FindingType.SIGNIFICANT_IMPROVEMENT;
                case WORSE -> FindingType.SIGNIFICANT_DETERIORATION;
                default -> FindingType.SIGNIFICANT_CHANGE;
            };
            String verb = switch (type) {
                case SIGNIFICANT_IMPROVEMENT -> "mejora";
                case SIGNIFICANT_DETERIORATION -> "empeora";
                default -> "varía";
            };
            String message = String.format("%s: «%s» %s %s respecto de la línea base «%s» (%s → %s).", metric.label(),
                    names.get(id), verb, change(metric.unit(), delta, relative), names.get(baseline.evaluationId()),
                    format(metric, base.value()), format(metric, value.value()));
            findings.add(new Ranked(2, magnitude(relative), position,
                    new Finding(type, id, metric, null, relative, message)));
        }
        return new MetricCell(id, value, delta, relative, assessment, relevant);
    }

    /** One side has a value, the other does not. Only payback has an obvious winner (recovered beats never). */
    private static DifferenceAssessment availabilityAssessment(ComparisonMetric metric, MetricValueDTO base,
                                                               MetricValueDTO value) {
        boolean payback = metric == ComparisonMetric.SIMPLE_PAYBACK || metric == ComparisonMetric.DISCOUNTED_PAYBACK;
        String notRecovered = "NOT_RECOVERED_WITHIN_HORIZON";
        if (payback && base.isAvailable() && notRecovered.equals(value.status())) return WORSE;
        if (payback && value.isAvailable() && notRecovered.equals(base.status())) return BETTER;
        return NOT_COMPARABLE;
    }

    private static DifferenceAssessment assess(MetricPreference preference, BigDecimal delta) {
        if (delta.signum() == 0) return EQUAL;
        return switch (preference) {
            case HIGHER_IS_BETTER -> delta.signum() > 0 ? BETTER : WORSE;
            case LOWER_IS_BETTER -> delta.signum() < 0 ? BETTER : WORSE;
            case NEUTRAL -> CHANGED;
        };
    }

    private static boolean exceeds(MetricUnit unit, BigDecimal delta, BigDecimal relative, BigDecimal relevance,
                                   BigDecimal rate) {
        return switch (unit) {
            case FRACTION -> delta.abs().compareTo(rate) >= 0;
            case YEARS -> delta.abs().compareTo(PAYBACK_THRESHOLD) >= 0;
            default -> relative == null ? delta.signum() != 0 : relative.abs().compareTo(relevance) >= 0;
        };
    }

    private static BigDecimal relative(BigDecimal delta, BigDecimal base) {
        if (base == null || base.signum() == 0) return null;
        return delta.divide(base.abs(), MC).setScale(6, RoundingMode.HALF_UP);
    }

    /** Changes from zero have no relative size; rank them above every finite one. */
    private static BigDecimal magnitude(BigDecimal relative) {
        return relative == null ? BigDecimal.valueOf(Long.MAX_VALUE) : relative.abs();
    }

    private static List<UUID> best(ComparisonMetric metric, ScenarioMetrics baseline, List<ScenarioMetrics> scenarios) {
        if (metric.preference() == MetricPreference.NEUTRAL) return List.of();
        List<ScenarioMetrics> candidates = scenarios.stream()
                .filter(s -> s.metric(metric).isAvailable())
                .filter(s -> metric.unit() != MetricUnit.CURRENCY || Objects.equals(s.currency(), baseline.currency()))
                .toList();
        if (candidates.size() < 2) return List.of();
        Comparator<BigDecimal> order = metric.preference() == MetricPreference.HIGHER_IS_BETTER
                ? Comparator.naturalOrder() : Comparator.reverseOrder();
        BigDecimal top = candidates.stream().map(s -> s.metric(metric).value()).max(order).orElseThrow();
        List<UUID> best = candidates.stream().filter(s -> s.metric(metric).value().compareTo(top) == 0)
                .map(ScenarioMetrics::evaluationId).toList();
        return best.size() == candidates.size() ? List.of() : best;
    }

    // ---------------------------------------------------------------- nodes

    private List<NodeRow> compareNodes(List<ScenarioMetrics> scenarios, BigDecimal relevance, Map<UUID, String> names,
                                       List<Ranked> findings) {
        ScenarioMetrics baseline = scenarios.getFirst();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        scenarios.forEach(s -> keys.addAll(s.nodes().keySet()));
        List<NodeRow> rows = new ArrayList<>();
        for (String key : keys) {
            ScenarioMetrics.Node reference = scenarios.stream().map(s -> s.nodes().get(key)).filter(Objects::nonNull)
                    .findFirst().orElseThrow();
            String name = reference.name() == null ? key : reference.name();
            ScenarioMetrics.Node baseNode = baseline.nodes().get(key);
            BigDecimal baseOutput = baseNode == null || baseNode.flows() == null ? null : baseNode.flows().output();
            List<NodeCell> cells = new ArrayList<>();
            for (int i = 0; i < scenarios.size(); i++) {
                ScenarioMetrics scenario = scenarios.get(i);
                ScenarioMetrics.Node node = scenario.nodes().get(key);
                NodeFlowDTO flows = node == null ? null : node.flows();
                if (i == 0) {
                    cells.add(new NodeCell(scenario.evaluationId(), node != null, flows, null, null, false));
                    continue;
                }
                UUID id = scenario.evaluationId();
                if ((node == null) != (baseNode == null)) {
                    boolean added = baseNode == null;
                    String message = added
                            ? String.format("El nodo «%s» existe en «%s» pero no en la línea base «%s».", name,
                                    names.get(id), names.get(baseline.evaluationId()))
                            : String.format("El nodo «%s» de la línea base «%s» no está en «%s».", name,
                                    names.get(baseline.evaluationId()), names.get(id));
                    findings.add(new Ranked(3, BigDecimal.valueOf(Long.MAX_VALUE), i, new Finding(
                            added ? FindingType.NODE_ADDED : FindingType.NODE_REMOVED, id, null, key, null, message)));
                    cells.add(new NodeCell(id, node != null, flows, null, null, true));
                    continue;
                }
                BigDecimal output = flows == null ? null : flows.output();
                if (output == null || baseOutput == null) {
                    cells.add(new NodeCell(id, node != null, flows, null, null, false));
                    continue;
                }
                BigDecimal delta = output.subtract(baseOutput);
                BigDecimal relative = relative(delta, baseOutput);
                boolean relevant = delta.signum() != 0
                        && (relative == null || relative.abs().compareTo(relevance) >= 0);
                if (relevant) {
                    String message = String.format("Nodo «%s»: la salida en «%s» varía %s respecto de la línea base «%s» (%s → %s).",
                            name, names.get(id), change(MetricUnit.SIMULATOR_UNIT, delta, relative),
                            names.get(baseline.evaluationId()), number(baseOutput, 2), number(output, 2));
                    findings.add(new Ranked(3, magnitude(relative), i,
                            new Finding(FindingType.NODE_FLOW_CHANGE, id, null, key, relative, message)));
                }
                cells.add(new NodeCell(id, true, flows, delta, relative, relevant));
            }
            rows.add(new NodeRow(key, name, reference.nodeType(), cells.stream().anyMatch(NodeCell::relevant), cells));
        }
        return rows;
    }

    // ---------------------------------------------------------------- warnings

    private static List<Warning> warnings(List<ScenarioMetrics> scenarios) {
        ScenarioMetrics baseline = scenarios.getFirst();
        List<Warning> warnings = new ArrayList<>();
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_CURRENCY,
                s -> !Objects.equals(s.currency(), baseline.currency()));
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_START_YEAR,
                s -> !Objects.equals(s.startYear(), baseline.startYear()));
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_HORIZON,
                s -> !Objects.equals(s.years(), baseline.years()));
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_WACC, s -> s.wacc() == null || baseline.wacc() == null
                ? s.wacc() != baseline.wacc() : s.wacc().compareTo(baseline.wacc()) != 0);
        differs(warnings, scenarios, ComparabilityWarning.MIXED_EVALUATION_MODES, s -> s.mode() != baseline.mode());
        differs(warnings, scenarios, ComparabilityWarning.DIFFERENT_SIMULATION_VERSION,
                s -> s.simulationAlgorithm() != null && baseline.simulationAlgorithm() != null
                        && (!s.simulationAlgorithm().equals(baseline.simulationAlgorithm())
                        || !Objects.equals(s.simulationCalculationVersion(), baseline.simulationCalculationVersion())));
        List<UUID> incomplete = scenarios.stream().filter(ScenarioMetrics::incomplete).map(ScenarioMetrics::evaluationId).toList();
        if (!incomplete.isEmpty()) {
            warnings.add(new Warning(ComparabilityWarning.INCOMPLETE_RESULTS,
                    ComparabilityWarning.INCOMPLETE_RESULTS.message(), incomplete));
        }
        return warnings;
    }

    private static void differs(List<Warning> warnings, List<ScenarioMetrics> scenarios, ComparabilityWarning code,
                                java.util.function.Predicate<ScenarioMetrics> differs) {
        List<UUID> ids = scenarios.stream().skip(1).filter(differs).map(ScenarioMetrics::evaluationId).toList();
        if (!ids.isEmpty()) warnings.add(new Warning(code, code.message(), ids));
    }

    // ---------------------------------------------------------------- text

    /** Version names, disambiguated when two evaluations of the same version are compared. */
    private static Map<UUID, String> displayNames(List<ScenarioMetrics> scenarios) {
        Map<String, Long> counts = new HashMap<>();
        scenarios.forEach(s -> counts.merge(String.valueOf(s.versionName()), 1L, Long::sum));
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < scenarios.size(); i++) {
            ScenarioMetrics s = scenarios.get(i);
            String name = String.valueOf(s.versionName());
            names.put(s.evaluationId(), counts.get(name) > 1 ? name + " #" + (i + 1) : name);
        }
        return names;
    }

    private static String change(MetricUnit unit, BigDecimal delta, BigDecimal relative) {
        return switch (unit) {
            case FRACTION -> number(delta.abs().movePointRight(2), 2) + " p.p.";
            case YEARS -> number(delta.abs(), 0) + (delta.abs().compareTo(BigDecimal.ONE) == 0 ? " año" : " años");
            default -> relative == null ? "desde cero" : number(relative.abs().movePointRight(2), 1) + " %";
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
