package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.util.*;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.enerscope.comparison.service.ComparisonEngine.Measurement;
import org.enerscope.economic.dto.EvaluationDTO.Snapshot;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.*;
import org.enerscope.economic.model.results.EconomicResult;
import org.enerscope.simulator.AnnualNodeMetrics;
import org.springframework.stereotype.Component;

@Component
public class ComparisonMeasurements {
    public Measurement extract(Metric metric, Snapshot snapshot, EconomicResult result, List<AnnualNodeMetrics> raw, Cell cell) {
        var c = snapshot.configuration();
        if (c == null || result == null) return missing(metric, "Faltan resultados completos.");
        return switch (metric) {
            case NPV -> measured(result.npv(), c.currency());
            case IRR -> result.indicators() != null && result.indicators().irr() != null && result.indicators().irr().status() == IrrStatus.CALCULATED
                    ? measured(result.indicators().irr().rate().multiply(ComparisonEngine.HUNDRED), "%") : missing(metric, "La TIR no está definida en todas las rondas.");
            case PAYBACK -> result.indicators() != null && result.indicators().simplePayback() != null && result.indicators().simplePayback().status() == PaybackStatus.RECOVERED
                    ? measured(BigDecimal.valueOf(result.indicators().simplePayback().period()), "años") : missing(metric, "El recupero simple no está definido en todas las rondas dentro del horizonte.");
            case INVESTMENT, OPERATING_COST -> expenses(metric, c, result);
            case DELIVERED_VOLUME -> delivery(snapshot, raw, cell);
        };
    }

    private Measurement expenses(Metric metric, EconomicConfiguration c, EconomicResult result) {
        if (result.entries() == null || c.boundary() == null) return missing(metric, "Faltan movimientos económicos.");
        BigDecimal total = BigDecimal.ZERO;
        for (var e : result.entries()) {
            if (e == null || e.recognitionDate() == null || e.amount() == null) return missing(metric, "Hay movimientos incompletos.");
            if (!c.boundary().contains(e.taxEntityId()) || (e.counterpartyId() != null && c.boundary().contains(e.counterpartyId()))) continue;
            if (e.recognitionDate().getYear() < c.startYear() || e.recognitionDate().getYear() > c.startYear() + c.years()) continue;
            if (e.direction() == Direction.EXPENSE && e.cashClassification() == CashClassification.CASH
                    && ((e.taxClassification() == TaxClassification.CAPITALIZABLE) == (metric == Metric.INVESTMENT))) total = total.add(e.amount());
        }
        return measured(total, c.currency());
    }

    private Measurement delivery(Snapshot snapshot, List<AnnualNodeMetrics> raw, Cell cell) {
        var metric = Metric.DELIVERED_VOLUME;
        var c = snapshot.configuration();
        if (cell.deliveryNodeIds().isEmpty()) return missing(metric, "Seleccioná los puntos de entrega para medir el volumen.");
        if (raw == null || c.conversions() == null || snapshot.physicalNodes() == null || snapshot.physicalConnections() == null) return missing(metric, "Faltan métricas, conversiones o topología guardada.");
        Set<String> nodeIds = new HashSet<>();
        snapshot.physicalNodes().forEach(n -> nodeIds.add(n.path("id").asText()));
        Map<String, List<String>> edges = new HashMap<>();
        for (var edge : snapshot.physicalConnections()) {
            if (!edge.hasNonNull("fromNodeId") || !edge.hasNonNull("toNodeId")) return missing(metric, "No se puede verificar la topología guardada.");
            edges.computeIfAbsent(edge.path("fromNodeId").asText(), k -> new ArrayList<>()).add(edge.path("toNodeId").asText());
        }
        Set<String> selected = new HashSet<>(); cell.deliveryNodeIds().forEach(n -> selected.add(n.toString()));
        if (!nodeIds.containsAll(selected)) return missing(metric, "Un punto de entrega no pertenece a la evaluación.");
        for (String node : selected) {
            Set<String> visited = new HashSet<>(); Deque<String> queue = new ArrayDeque<>(edges.getOrDefault(node, List.of()));
            while (!queue.isEmpty()) {
                String next = queue.removeFirst(); if (!visited.add(next)) continue;
                if (selected.contains(next)) return missing(metric, "Los puntos de entrega están conectados en serie o en un ciclo: el volumen se contaría más de una vez.");
                queue.addAll(edges.getOrDefault(next, List.of()));
            }
        }
        BigDecimal total = BigDecimal.ZERO; String unit = null;
        for (UUID node : cell.deliveryNodeIds()) {
            var conversions = c.conversions().stream().filter(v -> v.nodeId().equals(node) && v.metric() == Driver.OUTPUT_VOLUME && v.annualQuantity() == null).toList();
            if (conversions.size() != 1 || conversions.getFirst().factor() == null || conversions.getFirst().factor().signum() <= 0 || conversions.getFirst().unit() == null || conversions.getFirst().unit().isBlank()) return missing(metric, "Cada punto de entrega requiere una conversión explícita de OUTPUT_VOLUME observado.");
            var conversion = conversions.getFirst();
            if (unit != null && !unit.equals(conversion.unit())) return missing(metric, "Los puntos de entrega tienen unidades diferentes.");
            unit = conversion.unit();
            for (int period = 1; period <= c.years(); period++) {
                int p = period;
                var rows = raw.stream().filter(r -> r.nodeId().equals(node) && r.period() == p).toList();
                if (rows.size() != 1 || rows.getFirst().output() == null) return missing(metric, "El volumen tiene períodos faltantes o duplicados.");
                total = total.add(rows.getFirst().output().multiply(conversion.factor()));
            }
        }
        return measured(total, unit);
    }

    public Measurement average(Metric metric, List<Measurement> values) {
        if (values.isEmpty()) return missing(metric, "No hay rondas completas.");
        String unit = values.getFirst().unit(); BigDecimal total = BigDecimal.ZERO;
        for (var value : values) {
            if (value.value() == null || value.issue() != null) return value;
            if (!Objects.equals(unit, value.unit())) return missing(metric, "Unidades incompatibles entre rondas.");
            total = total.add(value.value());
        }
        return measured(total.divide(BigDecimal.valueOf(values.size()), ComparisonEngine.MC), unit);
    }

    private Measurement measured(BigDecimal value, String unit) {
        return new Measurement(value, unit, value == null ? "Valor no disponible." : null);
    }

    private Measurement missing(Metric metric, String issue) {
        return new Measurement(null, "", metric + ": " + issue);
    }
}
