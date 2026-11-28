package org.enerscope.comparison.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.enerscope.comparison.dto.ComparisonResultDTO;
import org.enerscope.comparison.dto.ComparisonResultDTO.MetricCell;
import org.enerscope.comparison.dto.ComparisonResultDTO.MetricRow;
import org.enerscope.comparison.model.enums.*;
import org.junit.jupiter.api.Test;

import static org.enerscope.comparison.model.enums.ComparisonMetric.*;
import static org.enerscope.comparison.model.enums.Standing.*;
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
    void ranksEveryScenarioAndMeasuresItsDistanceToTheBest() {
        var austral = scenario("Austral", Map.of(NPV, "300"));
        var expansion = scenario("Ampliación", Map.of(NPV, "800"));
        var base = scenario("Base", Map.of(NPV, "500"));
        var result = compare(austral, expansion, base);

        assertEquals(ComparisonMetric.values().length, result.metrics().size());
        assertEquals(BEST, cell(result, NPV, expansion).standing());
        assertEquals(1, cell(result, NPV, expansion).rank());
        assertEquals(0, bd("0").compareTo(cell(result, NPV, expansion).gapToBest()));
        assertEquals(MIDDLE, cell(result, NPV, base).standing());
        assertEquals(2, cell(result, NPV, base).rank());
        assertEquals(0, bd("300").compareTo(cell(result, NPV, base).gapToBest()));
        assertEquals(WORST, cell(result, NPV, austral).standing());
        assertEquals(3, cell(result, NPV, austral).rank());
        assertEquals(0, bd("0.625").compareTo(cell(result, NPV, austral).relativeGapToBest()));
        assertEquals(List.of(expansion.evaluationId()), row(result, NPV).bestEvaluationIds());
        assertEquals(List.of(austral.evaluationId()), row(result, NPV).worstEvaluationIds());
        assertTrue(result.scenarios().get(1).bestIn().contains(NPV));
    }

    @Test
    void lowerIsBetterIndicatorsRankTheSmallestFirstAndTiesShareTheRank() {
        var a = scenario("A", Map.of(CAPEX, "1000"));
        var b = scenario("B", Map.of(CAPEX, "1800"));
        var c = scenario("C", Map.of(CAPEX, "1000"));
        var result = compare(a, b, c);

        assertEquals(1, cell(result, CAPEX, a).rank());
        assertEquals(1, cell(result, CAPEX, c).rank());
        assertEquals(3, cell(result, CAPEX, b).rank());
        assertEquals(List.of(a.evaluationId(), c.evaluationId()), row(result, CAPEX).bestEvaluationIds());
        assertEquals(0, bd("0.8").compareTo(cell(result, CAPEX, b).relativeGapToBest()));
    }

    @Test
    void aRelevantGapIsAFindingThatNamesTheBestAndTheWorst() {
        var base = scenario("Base", Map.of(NPV, "1000"));
        var other = scenario("Alternativa", Map.of(NPV, "1300"));
        var result = compare(base, other);

        var finding = result.findings().stream().filter(f -> f.metric() == NPV).findFirst().orElseThrow();
        assertEquals(FindingType.LARGE_GAP, finding.type());
        assertEquals(List.of(other.evaluationId(), base.evaluationId()), finding.evaluationIds());
        assertTrue(finding.message().contains("«Alternativa» es el mejor"), finding.message());
        assertTrue(finding.message().contains("«Base» el peor"), finding.message());
        assertFalse(finding.message().contains("referencia"), finding.message());
        assertTrue(row(result, NPV).relevant());
    }

    @Test
    void smallGapsAreNotRelevantUnlessTheThresholdIsLower() {
        var base = scenario("Base", Map.of(NPV, "1000"));
        var other = scenario("Alternativa", Map.of(NPV, "1040"));

        assertFalse(row(compare(base, other), NPV).relevant());
        assertTrue(compare(base, other).findings().stream().noneMatch(f -> f.metric() == NPV));
        assertTrue(row(calculator.compare(List.of(base, other), bd("0.03"), null), NPV).relevant());
    }

    @Test
    void ratesUsePercentagePointsAndPaybacksWholeYears() {
        var base = scenario("Base", Map.of(IRR, "0.100", ROI, "0.20", SIMPLE_PAYBACK, "4"));
        var other = scenario("Alternativa", Map.of(IRR, "0.105", ROI, "0.23", SIMPLE_PAYBACK, "3"));
        var result = compare(base, other);

        assertFalse(row(result, IRR).relevant(), "0.5 p.p. is under the 1 p.p. default");
        assertTrue(row(result, ROI).relevant());
        assertEquals(BEST, cell(result, SIMPLE_PAYBACK, other).standing());
        assertTrue(row(result, SIMPLE_PAYBACK).relevant());
        var roi = result.findings().stream().filter(f -> f.metric() == ROI).findFirst().orElseThrow();
        assertTrue(roi.message().contains("p.p."), roi.message());
    }

    @Test
    void npvPositiveForSomeAndNotForOthersIsTheFirstFinding() {
        var base = scenario("Base", Map.of(NPV, "100", OPEX, "100"));
        var other = scenario("Conservador", Map.of(NPV, "-20", OPEX, "900"));
        var result = compare(base, other);

        var first = result.findings().getFirst();
        assertEquals(FindingType.NPV_SIGN_SPLIT, first.type());
        assertTrue(first.message().contains("«Base» genera valor"), first.message());
        assertTrue(first.message().contains("«Conservador» pierde valor"), first.message());
        assertEquals(WORST, cell(result, NPV, other).standing());
    }

    @Test
    void paybackNeverRecoveredByOneScenarioIsAnAvailabilitySplit() {
        var base = scenario("Base", Map.of(SIMPLE_PAYBACK, "3"));
        var other = scenario("Conservador", Map.of(SIMPLE_PAYBACK, "NOT_RECOVERED_WITHIN_HORIZON"));
        var result = compare(base, other);

        var finding = result.findings().stream().filter(f -> f.metric() == SIMPLE_PAYBACK).findFirst().orElseThrow();
        assertEquals(FindingType.AVAILABILITY_SPLIT, finding.type());
        assertEquals(List.of(other.evaluationId()), finding.evaluationIds());
        assertTrue(finding.message().contains("no se recupera dentro del horizonte en «Conservador»"), finding.message());
        assertEquals(UNRANKED, cell(result, SIMPLE_PAYBACK, other).standing());
        assertTrue(row(result, SIMPLE_PAYBACK).relevant());
    }

    @Test
    void missingDataIsNeverAFindingButIsWarned() {
        var base = scenario("Base", Map.of(CAPEX, "100"));
        var other = scenario("Alternativa", Map.of(CAPEX, "NOT_AVAILABLE"));
        var result = compare(base, other);

        assertEquals(UNRANKED, cell(result, CAPEX, other).standing());
        assertFalse(row(result, CAPEX).relevant());
        assertTrue(result.findings().stream().noneMatch(f -> f.metric() == CAPEX));
        assertTrue(result.warnings().stream().anyMatch(w -> w.code() == ComparabilityWarning.INCOMPLETE_RESULTS
                && w.evaluationIds().equals(List.of(other.evaluationId()))));
    }

    @Test
    void amountsInDifferentCurrenciesAreNotRankedButRatesAre() {
        var base = scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "100", IRR, "0.10"), Map.of());
        var other = scenario("Alternativa", "ARS", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "90000", IRR, "0.20"), Map.of());
        var result = compare(base, other);

        assertEquals(NOT_COMPARABLE, cell(result, NPV, other).standing());
        assertNull(cell(result, NPV, other).gapToBest());
        assertTrue(row(result, NPV).bestEvaluationIds().isEmpty());
        assertEquals(BEST, cell(result, IRR, other).standing());
        var warning = result.warnings().stream().filter(w -> w.code() == ComparabilityWarning.DIFFERENT_CURRENCY).findFirst().orElseThrow();
        assertEquals(List.of(base.evaluationId(), other.evaluationId()), warning.evaluationIds());
    }

    @Test
    void neutralIndicatorsAreReportedWithoutAWinner() {
        var base = scenario("Base", Map.of(TAXES, "100"));
        var other = scenario("Alternativa", Map.of(TAXES, "300"));
        var result = compare(base, other);

        assertEquals(UNRANKED, cell(result, TAXES, other).standing());
        assertTrue(row(result, TAXES).bestEvaluationIds().isEmpty());
        var finding = result.findings().stream().filter(f -> f.metric() == TAXES).findFirst().orElseThrow();
        assertEquals(FindingType.LARGE_GAP, finding.type());
        assertTrue(finding.message().contains("va de 100,00 en «Base» a 300,00 en «Alternativa»"), finding.message());
    }

    @Test
    void identicalScenariosTieWithoutWinnerFindingOrWarning() {
        var result = compare(scenario("A", Map.of()), scenario("B", Map.of()));

        assertTrue(result.metrics().stream().allMatch(r -> r.bestEvaluationIds().isEmpty() && !r.relevant()));
        assertTrue(result.metrics().stream().filter(r -> r.preference() != MetricPreference.NEUTRAL)
                .allMatch(r -> r.values().stream().allMatch(c -> c.standing() == TIED)));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void nodesInSomeScenariosOnlyAndBigOutputGapsAreSignalled() {
        var base = scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(),
                Map.of("w1", node("w1", "Pozo 1", "100"), "w2", node("w2", "Pozo 2", "50")));
        var other = scenario("Alternativa", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(),
                Map.of("w1", node("w1", "Pozo 1", "130"), "w3", node("w3", "Pozo 3", "70")));
        var result = compare(base, other);

        assertEquals(3, result.nodes().size());
        assertTrue(result.nodes().stream().allMatch(ComparisonResultDTO.NodeRow::relevant));
        var w3 = result.findings().stream().filter(f -> "w3".equals(f.nodeKey())).findFirst().orElseThrow();
        assertEquals(FindingType.NODE_PARTIAL, w3.type());
        assertTrue(w3.message().contains("solo está en «Alternativa»"), w3.message());
        var w1 = result.findings().stream().filter(f -> "w1".equals(f.nodeKey())).findFirst().orElseThrow();
        assertEquals(FindingType.NODE_FLOW_GAP, w1.type());
        assertTrue(w1.message().contains("va de 100,00 en «Base» a 130,00 en «Alternativa»"), w1.message());
    }

    @Test
    void scenariosThatDisagreeOnHorizonOrModeAreWarnedNamingAllOfThem() {
        var base = scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(), Map.of());
        var other = scenario("Alternativa", "USD", 15, EvaluationMode.PROBABILISTIC, Map.of(), Map.of());
        var warnings = compare(base, other).warnings();

        var codes = warnings.stream().map(ComparisonResultDTO.Warning::code).toList();
        assertTrue(codes.contains(ComparabilityWarning.DIFFERENT_HORIZON));
        assertTrue(codes.contains(ComparabilityWarning.MIXED_EVALUATION_MODES));
        assertFalse(codes.contains(ComparabilityWarning.DIFFERENT_CURRENCY));
        assertTrue(warnings.stream().allMatch(w -> w.evaluationIds().size() == 2));
    }

    @Test
    void scenariosAreNamedByTheirScenarioNameAndTheVersionIsAddedWhenTheNamesRepeat() {
        // The default scenarios of two versions share a name: the version tells them apart.
        var base = namedScenario(scenario("Neuquina", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "100"), Map.of()), "Escenario base", false);
        var other = namedScenario(scenario("Austral", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "300"), Map.of()), "Escenario base", false);
        var finding = compare(base, other).findings().stream().filter(f -> f.metric() == NPV).findFirst().orElseThrow();
        assertTrue(finding.message().contains("«Austral · Escenario base» es el mejor"), finding.message());
        assertTrue(finding.message().contains("«Neuquina · Escenario base» el peor"), finding.message());

        // Distinct scenario names of the same version read as just the scenario name.
        var conservative = namedScenario(scenario("Neuquina", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "100"), Map.of()), "Conservador", false);
        var ambitious = namedScenario(scenario("Neuquina", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "300"), Map.of()), "Ambicioso", false);
        var named = compare(conservative, ambitious);
        assertTrue(named.findings().stream().filter(f -> f.metric() == NPV).findFirst().orElseThrow().message().contains("«Ambicioso» es el mejor"));
        assertEquals("Conservador", named.scenarios().get(0).scenarioName());
    }

    @Test
    void theDeletedFlagTravelsToTheResultAndAScenarioWithoutAnameFallsBackToItsVersion() {
        var gone = namedScenario(scenario("Base", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "100"), Map.of()), "Borrado", true);
        var legacy = scenario("Ampliación", "USD", 10, EvaluationMode.DETERMINISTIC, Map.of(NPV, "300"), Map.of());

        var result = compare(gone, legacy);

        assertTrue(result.scenarios().get(0).deleted());
        assertFalse(result.scenarios().get(1).deleted());
        assertTrue(result.findings().stream().filter(f -> f.metric() == NPV).findFirst().orElseThrow().message().contains("«Ampliación» es el mejor"));
    }

    @Test
    void fewerThanTwoScenariosAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.compare(List.of(scenario("A", Map.of())), null, null));
        assertThrows(IllegalArgumentException.class, () -> calculator.compare(null, null, null));
    }
}
