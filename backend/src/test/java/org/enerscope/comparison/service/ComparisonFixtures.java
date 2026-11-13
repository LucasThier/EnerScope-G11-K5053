package org.enerscope.comparison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.enerscope.comparison.dto.MetricValueDTO;
import org.enerscope.comparison.dto.NodeFlowDTO;
import org.enerscope.comparison.model.enums.ComparisonMetric;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.economic.dto.EvaluationDTO.Snapshot;
import org.enerscope.economic.model.EconomicEntry;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.*;
import org.enerscope.economic.model.results.*;
import org.enerscope.simulator.AnnualNodeMetrics;

/** Small, hand-checkable evaluations and measured scenarios for the comparison tests. */
final class ComparisonFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    static final UUID WELL = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID PLANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID WELL_IDENTITY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID PLANT_IDENTITY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private ComparisonFixtures() {}

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    static EconomicConfiguration configuration(String currency, int startYear, int years, String wacc) {
        return new EconomicConfiguration(startYear, years, currency, bd(wacc), List.of(), Set.of("OWNER"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    static EconomicEntry entry(String id, String entity, String counterparty, Direction direction,
                               TaxClassification tax, CashClassification cash, int year, String amount) {
        LocalDate date = LocalDate.of(year, 6, 30);
        return new EconomicEntry(id, WELL, entity, counterparty, id, id, null, direction, tax, cash, date,
                cash == CashClassification.CASH ? date : null, bd(amount), "USD", BigDecimal.ONE);
    }

    static PeriodEconomicResult period(int t, int year, String afterTax, String taxes, String cash) {
        BigDecimal zero = BigDecimal.ZERO;
        return new PeriodEconomicResult(t, year, zero, zero, zero, zero, bd(taxes), bd(afterTax), zero, zero, zero,
                zero, bd(cash), bd(cash));
    }

    static EconomicIndicators indicators(IrrResult irr, PaybackResult simple, PaybackResult discounted) {
        return new EconomicIndicators(irr, simple, discounted, 1, IndicatorOrigin.STORED);
    }

    static PaybackResult recovered(int period) {
        return new PaybackResult(PaybackStatus.RECOVERED, period, 2030 + period, false);
    }

    static PaybackResult notRecovered() {
        return new PaybackResult(PaybackStatus.NOT_RECOVERED_WITHIN_HORIZON, null, null, false);
    }

    /**
     * Horizon 2030–2031, boundary {OWNER}. Counted: CAPEX 1000, OPEX 100, revenue 500.
     * Not counted: depreciation (non-cash), an entity outside the boundary, an internal
     * transfer and an entry after the horizon. Net cash -400, after-tax result 200, taxes 50.
     */
    static EconomicResult result(String npv, IrrResult irr, PaybackResult simple) {
        List<EconomicEntry> entries = List.of(
                entry("asset", "OWNER", null, Direction.EXPENSE, TaxClassification.CAPITALIZABLE, CashClassification.CASH, 2030, "1000"),
                entry("opex", "OWNER", null, Direction.EXPENSE, TaxClassification.DEDUCTIBLE, CashClassification.CASH, 2031, "100"),
                entry("depreciation", "OWNER", null, Direction.EXPENSE, TaxClassification.DEDUCTIBLE, CashClassification.NON_CASH, 2031, "200"),
                entry("sales", "OWNER", null, Direction.INCOME, TaxClassification.TAXABLE, CashClassification.CASH, 2031, "500"),
                entry("partner", "PARTNER", null, Direction.EXPENSE, TaxClassification.DEDUCTIBLE, CashClassification.CASH, 2031, "999"),
                entry("internal", "OWNER", "OWNER", Direction.EXPENSE, TaxClassification.DEDUCTIBLE, CashClassification.CASH, 2031, "777"),
                entry("late", "OWNER", null, Direction.EXPENSE, TaxClassification.DEDUCTIBLE, CashClassification.CASH, 2040, "555"));
        List<PeriodEconomicResult> periods = List.of(period(0, 2030, "-50", "0", "-1000"), period(1, 2031, "250", "50", "600"));
        return new EconomicResult(entries, List.of(), periods, List.of(), List.of(), bd(npv),
                indicators(irr, simple, notRecovered()));
    }

    static List<AnnualNodeMetrics> raw(String wellOutput, String plantOutput) {
        BigDecimal zero = BigDecimal.ZERO;
        return List.of(
                new AnnualNodeMetrics(WELL, 1, zero, bd(wellOutput), zero, bd("1"), bd("8000"), bd("2")),
                new AnnualNodeMetrics(PLANT, 1, bd(wellOutput), bd(plantOutput), bd(plantOutput), bd("3"), bd("8500"), bd("1")));
    }

    static JsonNode nodes() {
        ArrayNode nodes = MAPPER.createArrayNode();
        nodes.addObject().put("id", WELL.toString()).put("identityId", WELL_IDENTITY.toString()).put("name", "Pozo 1")
                .putObject("type").put("nodeType", "WELL");
        nodes.addObject().put("id", PLANT.toString()).put("identityId", PLANT_IDENTITY.toString()).put("name", "Planta")
                .putObject("type").put("nodeType", "TREATMENT_PLANT");
        return nodes;
    }

    static JsonNode connections() {
        ArrayNode connections = MAPPER.createArrayNode();
        connections.addObject().put("fromNodeId", WELL.toString()).put("toNodeId", PLANT.toString());
        return connections;
    }

    static Snapshot deterministic(EconomicConfiguration configuration, EconomicResult result, List<AnnualNodeMetrics> raw) {
        return new Snapshot(2, configuration, nodes(), connections(), raw, result,
                new SimulationMetadata(1, "1", "ALGO", 1), null);
    }

    static Snapshot probabilistic(EconomicConfiguration configuration) {
        return new Snapshot(3, configuration, nodes(), connections(), null, null,
                new SimulationMetadata(2, "1", "ALGO", 1),
                new ProbabilisticSummary(2, null, null, null, Map.of(), null, null, List.of(), List.of()));
    }

    // ---------------------------------------------------------------- measured scenarios

    /** A measured scenario where every indicator is available with the given values (others default to 100). */
    static ScenarioMetrics scenario(String name, Map<ComparisonMetric, String> values) {
        return scenario(name, "USD", 10, EvaluationMode.DETERMINISTIC, values, Map.of());
    }

    static ScenarioMetrics scenario(String name, String currency, int years, EvaluationMode mode,
                                    Map<ComparisonMetric, String> values, Map<String, ScenarioMetrics.Node> nodes) {
        Map<ComparisonMetric, MetricValueDTO> metrics = new EnumMap<>(ComparisonMetric.class);
        for (ComparisonMetric metric : ComparisonMetric.values()) {
            String raw = values.getOrDefault(metric, "100");
            metrics.put(metric, raw.matches("-?[0-9.]+")
                    ? new MetricValueDTO(bd(raw), MetricValueDTO.AVAILABLE, 1, 1, null)
                    : new MetricValueDTO(null, raw, 0, 1, null));
        }
        return new ScenarioMetrics(UUID.randomUUID(), UUID.randomUUID(), name, Instant.parse("2026-11-01T00:00:00Z"),
                mode, 1, currency, 2030, years, bd("0.1"), "ALGO", 1, metrics, new LinkedHashMap<>(nodes), List.of());
    }

    static ScenarioMetrics.Node node(String key, String name, String output) {
        BigDecimal zero = BigDecimal.ZERO;
        return new ScenarioMetrics.Node(key, name, "WELL", new NodeFlowDTO(zero, bd(output), zero, zero, zero, zero));
    }
}
