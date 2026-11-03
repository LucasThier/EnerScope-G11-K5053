package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.springframework.stereotype.Component;

/** Pure scoring; source values must have been extracted from authorized saved evaluations. */
@Component
public class ComparisonEngine {
    static final MathContext MC = MathContext.DECIMAL128;

    static final BigDecimal HUNDRED = new BigDecimal("100");

    public record Measurement(BigDecimal value,
                              String unit,
                              String issue) {}

    public record Observation(String alternativeId,
                              String contextId,
                              UUID versionId,
                              UUID evaluationId,
                              int rounds,
                              Map<Metric, Measurement> values,
                              List<String> issues) {}

    public void validate(Request request) {
        require(request != null, "La comparación es obligatoria.");
        text(request.name(), "Nombre"); text(request.scopeDescription(), "Alcance y supuestos");
        require(request.assumptionsConfirmed(), "Confirmá que las evaluaciones representan el mismo alcance y los contextos indicados.");
        require(request.contexts() != null && !request.contexts().isEmpty() && request.contexts().size() <= 10, "Seleccioná entre 1 y 10 contextos.");
        require(request.alternatives() != null && request.alternatives().size() >= 2 && request.alternatives().size() <= 20, "Seleccioná entre 2 y 20 alternativas.");
        Set<String> contexts = new HashSet<>(), alternatives = new HashSet<>();
        BigDecimal probabilities = BigDecimal.ZERO;
        for (var c : request.contexts()) {
            require(c != null, "Contexto inválido."); text(c.id(), "Identificador"); text(c.name(), "Contexto");
            require(contexts.add(c.id()), "Contexto duplicado.");
            require(c.probability() != null && c.probability().signum() > 0 && c.probability().compareTo(HUNDRED) <= 0, "Las probabilidades deben ser mayores que 0 y menores o iguales a 100.");
            probabilities = probabilities.add(c.probability());
        }
        require(probabilities.compareTo(HUNDRED) == 0, "Las probabilidades deben sumar 100 %.");
        for (var a : request.alternatives()) {
            require(a != null, "Alternativa inválida."); text(a.id(), "Identificador"); text(a.name(), "Alternativa");
            require(alternatives.add(a.id()), "Alternativa duplicada.");
            require(a.cells() != null && a.cells().size() == contexts.size(), "Completá todas las combinaciones de alternativa y contexto.");
            Set<String> seen = new HashSet<>();
            for (var cell : a.cells()) {
                require(cell != null && contexts.contains(cell.contextId()) && seen.add(cell.contextId()) && cell.versionId() != null && cell.evaluationId() != null, "Celda incompleta o duplicada.");
                require(cell.deliveryNodeIds() != null && cell.deliveryNodeIds().size() <= 100 && cell.deliveryNodeIds().stream().noneMatch(Objects::isNull), "Puntos de entrega inválidos.");
                require(new HashSet<>(cell.deliveryNodeIds()).size() == cell.deliveryNodeIds().size(), "Punto de entrega duplicado.");
            }
        }
        require(request.criteria() != null && !request.criteria().isEmpty() && request.criteria().size() <= Metric.values().length, "Elegí al menos un indicador.");
        BigDecimal weights = BigDecimal.ZERO; Set<Metric> metrics = new HashSet<>();
        for (var c : request.criteria()) {
            require(c != null && c.metric() != null && metrics.add(c.metric()), "Indicador inválido o duplicado.");
            require(c.weight() != null && c.weight().signum() >= 0 && c.weight().compareTo(HUNDRED) <= 0, "Peso inválido.");
            require(c.unfavorable() != null && c.favorable() != null, "Completá las referencias de desempeño.");
            boolean maximize = c.metric() == Metric.NPV || c.metric() == Metric.IRR || c.metric() == Metric.DELIVERED_VOLUME;
            require(maximize ? c.favorable().compareTo(c.unfavorable()) > 0 : c.favorable().compareTo(c.unfavorable()) < 0, "Las referencias no respetan la dirección del indicador " + c.metric());
            require(c.minimum() == null || c.maximum() == null || c.minimum().compareTo(c.maximum()) <= 0, "El mínimo no puede superar al máximo.");
            weights = weights.add(c.weight());
        }
        require(weights.compareTo(HUNDRED) == 0, "Los pesos deben sumar 100 %.");
    }

    public Result calculate(Request request, List<Observation> observations, String currency, int startYear, int years, List<String> compatibilityIssues) {
        validate(request);
        List<String> issues = new ArrayList<>(compatibilityIssues);
        List<AlternativeResult> results = new ArrayList<>();
        for (var alternative : request.alternatives()) {
            List<ContextResult> contexts = new ArrayList<>();
            BigDecimal expected = BigDecimal.ZERO, worst = HUNDRED;
            boolean eligible = true;
            for (var context : request.contexts()) {
                var o = observations.stream().filter(x -> x.alternativeId().equals(alternative.id()) && x.contextId().equals(context.id())).findFirst().orElseThrow();
                List<Value> values = new ArrayList<>();
                List<String> cellIssues = new ArrayList<>(o.issues());
                BigDecimal score = BigDecimal.ZERO;
                boolean meets = true;
                for (var criterion : request.criteria()) {
                    var m = o.values().get(criterion.metric());

                    if (m == null || m.value() == null || m.issue() != null) {
                        String issue = m == null || m.issue() == null ? "Indicador no disponible: " + criterion.metric() : m.issue();
                        cellIssues.add(issue);
                        values.add(new Value(criterion.metric(), null, m == null ? "" : m.unit(), null, null, issue, false));
                        continue;
                    }

                    BigDecimal normalized = m.value().subtract(criterion.unfavorable()).divide(criterion.favorable().subtract(criterion.unfavorable()), MC).multiply(HUNDRED).max(BigDecimal.ZERO).min(HUNDRED);

                    BigDecimal contribution = normalized.multiply(criterion.weight()).divide(HUNDRED, MC);

                    boolean within = (criterion.minimum() == null || m.value().compareTo(criterion.minimum()) >= 0) && (criterion.maximum() == null || m.value().compareTo(criterion.maximum()) <= 0);

                    meets &= within;
                    score = score.add(contribution);
                    values.add(new Value(criterion.metric(), m.value(), m.unit(), normalized, contribution, null, within));
                }
                if (!cellIssues.isEmpty())
                    issues.add(alternative.name() + " / " + context.name() + ": " + String.join("; ", cellIssues));
                eligible &= meets && cellIssues.isEmpty();
                expected = expected.add(score.multiply(context.probability()).divide(HUNDRED, MC));
                worst = worst.min(score);
                contexts.add(new ContextResult(context.id(), o.versionId(), o.evaluationId(), o.rounds(), values, cellIssues.isEmpty() ? score : null, meets && cellIssues.isEmpty(), cellIssues));
            }
            results.add(new AlternativeResult(alternative.id(), alternative.name(), contexts, expected, worst, eligible, null));
        }

        // One incomplete source blocks the recommendation for the complete comparison.
        boolean blocked = !issues.isEmpty();

        var eligibleScores = results.stream().filter(AlternativeResult::eligible).map(AlternativeResult::score).toList();
        List<AlternativeResult> ranked = new ArrayList<>(); List<String> winners = new ArrayList<>();

        for (var r : results) {
            Integer rank = blocked || !r.eligible() ? null : 1 + (int) eligibleScores.stream().filter(s -> s.compareTo(r.score()) > 0).count();
            if (rank != null && rank == 1) winners.add(r.id());
            ranked.add(new AlternativeResult(r.id(), r.name(), r.contexts(), blocked ? null : r.score(), blocked ? null : r.worstScore(), r.eligible(), rank));
        }
        return new Result(1, blocked ? "BLOCKED" : winners.isEmpty() ? "NO_ELIGIBLE_ALTERNATIVE" : "READY", currency, startYear, years, ranked, winners, issues);
    }

    static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalArgumentException(message);
    }

    private static void text(String value, String field) {
        require(value != null && !value.isBlank() && value.length() <= 500, field + ": ingresá entre 1 y 500 caracteres.");
    }
}
