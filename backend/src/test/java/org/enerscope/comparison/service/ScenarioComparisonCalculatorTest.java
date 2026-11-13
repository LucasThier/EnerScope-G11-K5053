package org.enerscope.comparison.service;

import java.util.List;
import java.util.Map;
import org.enerscope.comparison.dto.ComparisonResultDTO;
import org.enerscope.comparison.dto.ComparisonResultDTO.MetricCell;
import org.enerscope.comparison.dto.ComparisonResultDTO.MetricRow;
import org.enerscope.comparison.model.enums.*;
import org.junit.jupiter.api.Test;

import static org.enerscope.comparison.model.enums.ComparisonMetric.*;
import static org.enerscope.comparison.model.enums.DifferenceAssessment.*;
import static org.enerscope.comparison.service.ComparisonFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioComparisonCalculatorTest {

    final ScenarioComparisonCalculator calculator = new ScenarioComparisonCalculator();

    static MetricRow row(ComparisonResultDTO result, ComparisonMetric metric) {
        return result.metrics().stream().filter(r -> r.metric() == metric).findFirst().orElseThrow();
    }

    static MetricCell cell(ComparisonResultDTO result, ComparisonMetric metric, ScenarioMetrics scenario) {
        return row(result, metric).values().stream()
                .filter(c -> c.evaluationId().equals(scenario.evaluationId())).findFirst().orElseThrow();
    }

    ComparisonResultDTO compare(ScenarioMetrics... scenarios) {
        return calculator.compare(List.of(scenarios), null, null);
    }

    @Test
    void comparesEveryIndicatorAgainstTheBaselineWithDeltasAndBestScenario() {
        var base = scenario("Base", Map.of(NPV, "1000", CAPEX, "500"));
        var expansion = scenario("Ampliación", Map.of(NPV, "1500", CAPEX, "800"));
        var result = compare(base, expansion);

        assertEquals(base.evaluationId(), result.baselineEvaluationId());
        assertEquals(ComparisonMetric.values().length, result.metrics().size());
        assertEquals(BASELINE, cell(result, NPV, base).assessment());
        var npv = cell(result, NPV, expansion);
        assertEquals(0, bd("500").compareTo(npv.absoluteDelta()));
        assertEquals(0, bd("0.5").compareTo(npv.relativeDelta()));
        assertEquals(BETTER, npv.assessment());
        assertTrue(npv.relevant());
        assertEquals(WORSE, cell(result, CAPEX, expansion).assessment());
        assertEquals(List.of(expansion.evaluationId()), row(result, NPV).bestEvaluationIds());
        assertEquals(List.of(base.evaluationId()), row(result, CAPEX).bestEvaluationIds());
        assertTrue(result.scenarios().get(1).bestIn().contains(NPV));
        assertTrue(result.scenarios().get(0).baseline());
    }

    @Test
    void signalsRelevantImprovementsAndDeteriorationsAsFindings() {
        var base = scenario("Base", Map.of(NPV, "1000", OPEX, "200"));
        var other = scenario("Alternativa", Map.of(NPV, "1300", OPEX, "260"));
        var result = compare(base, other);

        var improvement = result.findings().stream().filter(f -> f.metric() == NPV).findFirst().orElseThrow();
        assertEquals(FindingType.SIGNIFICANT_IMPROVEMENT, improvement.type());
        assertEquals(other.evaluationId(), improvement.evaluationId());
        assertTrue(improvement.message().contains("VAN"), improvement.message());
        assertTrue(improvement.message().contains("mejora"), improvement.message());
        var deterioration = result.findings().stream().filter(f -> f.metric() == OPEX).findFirst().orElseThrow();
        assertEquals(FindingType.SIGNIFICANT_DETERIORATION, deterioration.type());
    }

    @Test
    void differencesBelowTheRelevanceThresholdAreNotFlagged() {
        var base = scenario("Base", Map.of(NPV, "1000"));
        var other = scenario("Alternativa", Map.of(NPV, "1040"));
        var result = compare(base, other);

        var npv = cell(result, NPV, other);
        assertEquals(BETTER, npv.assessment());
        assertFalse(npv.relevant());
        assertTrue(result.findings().stream().noneMatch(f -> f.metric() == NPV));
        // A custom threshold makes the same 4% difference relevant.
        var strict = calculator.compare(List.of(base, other), bd("0.03"), null);
        assertTrue(cell(strict, NPV, other).relevant());
    }

    @Test
    void ratesUsePercentagePointsAndPaybacksWholeYears() {
        var base = scenario("Base", Map.of(IRR, "0.100", ROI, "0.20", SIMPLE_PAYBACK, "4"));
        var other = scenario("Alternativa", Map.of(IRR, "0.105", ROI, "0.23", SIMPLE_PAYBACK, "3"));
        var result = compare(base, other);

        // IRR moves 0.5 p.p. (5% relative) — below the 1 p.p. default.
        assertFalse(cell(result, IRR, other).relevant());
        assertTrue(cell(result, ROI, other).relevant());
        var payback = cell(result, SIMPLE_PAYBACK, other);
        assertEquals(BETTER, payback.assessment());
        assertTrue(payback.relevant());
        var roiFinding = result.findings().stream().filter(f -> f.metric() == ROI).findFirst().orElseThrow();
        assertTrue(roiFinding.message().contains("p.p."), roiFinding.message());
    }

    @Test
    void npvSignChangeIsTheMostRelevantFinding() {
        var base = scenario("Base", Map.of(NPV, "100", OPEX, "100"));
        var other = scenario("Alternativa", Map.of(NPV, "-20", OPEX, "900"));
        var result = compare(base, other);

        assertEquals(FindingType.NPV_SIGN_CHANGE, result.findings().getFirst().type());
        assertTrue(cell(result, NPV, other).relevant());
        assertEquals(WORSE, cell(result, NPV, other).assessment());
    }

    @Test
    void paybackNeverRecoveredIsWorseAndReportedAsAvailabilityChange() {
        var base = scenario("Base", Map.of(SIMPLE_PAYBACK, "3"));
        var other = scenario("Alternativa", Map.of(SIMPLE_PAYBACK, "NOT_RECOVERED_WITHIN_HORIZON"));
        var result = compare(base, other);

        var payback = cell(result, SIMPLE_PAYBACK, other);
        assertEquals(WORSE, payback.assessment());
        assertTrue(payback.relevant());
        assertNull(payback.absoluteDelta());
        var finding = result.findings().stream().filter(f -> f.metric() == SIMPLE_PAYBACK).findFirst().orElseThrow();
        assertEquals(FindingType.AVAILABILITY_CHANGE, finding.type());
        assertTrue(finding.message().contains("no se recupera"), finding.message());
        assertEquals(List.of(), row(result, SIMPLE_PAYBACK).bestEvaluationIds());
    }

    @Test
    void missingDataIsNotComparableAndNeverAFinding() {
        var base = scenario("Base", Map.of(CAPEX, "100"));
        var other = scenario("Alternativa", Map.of(CAPEX, "NOT_AVAILABLE"));
        var result = compare(base, other);

        assertEquals(NOT_COMPARABLE, cell(result, CAPEX, other).assessment());
        assertFalse(cell(result, CAPEX, other).relevant());
        assertTrue(result.findings().stream().noneMatch(f -> f.metric() == CAPEX));
        assertTrue(result.warnings().stream().anyMatch(w -> w.code() == ComparabilityWarning.INCOMPLETE_RESULTS
                && w.evaluationIds().equals(List.of(other.evaluationId()))));
    }

    @Test
    void amountsInAnotherCurrencyAreNotComparedButRatesStillAre() {
        var base = scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "100", IRR, "0.10"), Map.of());
        var other = scenario("Alternativa", "ARS", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "90000", IRR, "0.20"), Map.of());
        var result = compare(base, other);

        assertEquals(NOT_COMPARABLE, cell(result, NPV, other).assessment());
        assertNull(cell(result, NPV, other).absoluteDelta());
        assertEquals(BETTER, cell(result, IRR, other).assessment());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code() == ComparabilityWarning.DIFFERENT_CURRENCY));
    }

    @Test
    void neutralIndicatorsChangeWithoutWinner() {
        var base = scenario("Base", Map.of(TAXES, "100"));
        var other = scenario("Alternativa", Map.of(TAXES, "300"));
        var result = compare(base, other);

        assertEquals(CHANGED, cell(result, TAXES, other).assessment());
        assertTrue(row(result, TAXES).bestEvaluationIds().isEmpty());
        var finding = result.findings().stream().filter(f -> f.metric() == TAXES).findFirst().orElseThrow();
        assertEquals(FindingType.SIGNIFICANT_CHANGE, finding.type());
    }

    @Test
    void equalValuesHaveNoWinnerAndNoFinding() {
        var result = compare(scenario("A", Map.of()), scenario("B", Map.of()));

        assertTrue(result.metrics().stream().allMatch(r -> r.bestEvaluationIds().isEmpty() && !r.relevant()));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void comparesThreeScenariosEachAgainstTheBaseline() {
        var base = scenario("Base", Map.of(NPV, "100"));
        var second = scenario("Segundo", Map.of(NPV, "200"));
        var third = scenario("Tercero", Map.of(NPV, "50"));
        var result = compare(base, second, third);

        assertEquals(3, row(result, NPV).values().size());
        assertEquals(0, bd("1").compareTo(cell(result, NPV, second).relativeDelta()));
        assertEquals(0, bd("-0.5").compareTo(cell(result, NPV, third).relativeDelta()));
        assertEquals(List.of(second.evaluationId()), row(result, NPV).bestEvaluationIds());
        // Bigger relative change first.
        var npvFindings = result.findings().stream().filter(f -> f.metric() == NPV).toList();
        assertEquals(second.evaluationId(), npvFindings.getFirst().evaluationId());
    }

    @Test
    void nodesAreMatchedByIdentityAndAddedRemovedOrChangedNodesAreSignalled() {
        var base = scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(),
                Map.of("w1", node("w1", "Pozo 1", "100"), "w2", node("w2", "Pozo 2", "50")));
        var other = scenario("Alternativa", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(),
                Map.of("w1", node("w1", "Pozo 1", "130"), "w3", node("w3", "Pozo 3", "70")));
        var result = compare(base, other);

        assertEquals(3, result.nodes().size());
        var w1 = result.nodes().stream().filter(n -> n.nodeKey().equals("w1")).findFirst().orElseThrow();
        assertTrue(w1.relevant());
        assertEquals(0, bd("30").compareTo(w1.values().get(1).outputDelta()));
        var types = result.findings().stream().map(ComparisonResultDTO.Finding::type).toList();
        assertTrue(types.containsAll(List.of(FindingType.NODE_ADDED, FindingType.NODE_REMOVED, FindingType.NODE_FLOW_CHANGE)));
        var w3 = result.nodes().stream().filter(n -> n.nodeKey().equals("w3")).findFirst().orElseThrow();
        assertFalse(w3.values().get(0).present());
        assertTrue(w3.values().get(1).present());
    }

    @Test
    void differentHorizonsAndModesProduceComparabilityWarnings() {
        var base = scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(), Map.of());
        var other = scenario("Alternativa", "USD", 15, EvaluationMode.PROBABILISTIC, Map.of(), Map.of());
        var codes = compare(base, other).warnings().stream().map(ComparisonResultDTO.Warning::code).toList();

        assertTrue(codes.contains(ComparabilityWarning.DIFFERENT_HORIZON));
        assertTrue(codes.contains(ComparabilityWarning.MIXED_EVALUATION_MODES));
        assertFalse(codes.contains(ComparabilityWarning.DIFFERENT_CURRENCY));
    }

    @Test
    void fewerThanTwoScenariosAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.compare(List.of(scenario("A", Map.of())), null, null));
        assertThrows(IllegalArgumentException.class, () -> calculator.compare(null, null, null));
    }
}
