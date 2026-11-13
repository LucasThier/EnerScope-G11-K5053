package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.comparison.dto.MetricValueDTO;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.economic.model.enums.IrrStatus;
import org.enerscope.economic.model.results.EconomicResult;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.economic.model.results.IrrResult;
import org.enerscope.economic.service.EconomicStatisticsCalculator;
import org.junit.jupiter.api.Test;

import static org.enerscope.comparison.model.enums.ComparisonMetric.*;
import static org.enerscope.comparison.service.ComparisonFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioMetricsExtractorTest {

    final ScenarioMetricsExtractor extractor = new ScenarioMetricsExtractor(new EconomicStatisticsCalculator());
    final UUID version = UUID.randomUUID();
    final UUID evaluation = UUID.randomUUID();

    ScenarioSource source(org.enerscope.economic.dto.EvaluationDTO.Snapshot snapshot, List<EconomicRoundResult> rounds) {
        return new ScenarioSource(version, "Base", evaluation, Instant.now(), snapshot, rounds);
    }

    static void assertValue(String expected, MetricValueDTO actual) {
        assertTrue(actual.isAvailable(), "expected a value but got " + actual.status());
        assertEquals(0, new BigDecimal(expected).compareTo(actual.value()), "expected " + expected + " but got " + actual.value());
    }

    @Test
    void deterministicEvaluationMeasuresFinancialIndicatorsCostsAndCashTotals() {
        var result = result("123.45", new IrrResult(IrrStatus.CALCULATED, bd("0.12")), recovered(1));
        var metrics = extractor.extract(source(deterministic(configuration("USD", 2030, 1, "0.1"), result, raw("100", "90")), List.of()));

        assertEquals(EvaluationMode.DETERMINISTIC, metrics.mode());
        assertEquals(1, metrics.rounds());
        assertValue("123.45", metrics.metric(NPV));
        assertValue("0.12", metrics.metric(IRR));
        assertValue("1", metrics.metric(SIMPLE_PAYBACK));
        assertEquals("NOT_RECOVERED_WITHIN_HORIZON", metrics.metric(DISCOUNTED_PAYBACK).status());
        assertNull(metrics.metric(DISCOUNTED_PAYBACK).value());
        assertValue("1000", metrics.metric(CAPEX));
        assertValue("100", metrics.metric(OPEX));
        assertValue("500", metrics.metric(REVENUE));
        assertValue("50", metrics.metric(TAXES));
        assertValue("-400", metrics.metric(NET_CASH_FLOW));
        assertValue("-0.4", metrics.metric(ROI));
        // (200 after tax / 2 periods) / 1000 invested
        assertValue("0.1", metrics.metric(ROE));
        assertNull(metrics.metric(NPV).distribution());
        assertFalse(metrics.incomplete());
    }

    @Test
    void physicalFlowsCountProductionAtSourceNodesOnlyAndMatchNodesByIdentity() {
        var result = result("1", new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1));
        var metrics = extractor.extract(source(deterministic(configuration("USD", 2030, 1, "0.1"), result, raw("100", "90")), List.of()));

        // The plant is fed by the well: its 90 must not be added to the well's 100.
        assertValue("100", metrics.metric(PRODUCED_VOLUME));
        assertValue("90", metrics.metric(EXPORTED_VOLUME));
        assertValue("4", metrics.metric(LOSSES));
        assertValue("16500", metrics.metric(OPERATING_HOURS));
        assertValue("3", metrics.metric(EVENTS));
        var well = metrics.nodes().get(WELL_IDENTITY.toString());
        assertEquals("Pozo 1", well.name());
        assertEquals("WELL", well.nodeType());
        assertEquals(0, bd("100").compareTo(well.flows().output()));
        var point = metrics.annual().stream().filter(p -> p.year() == 2031).findFirst().orElseThrow();
        assertEquals(0, bd("600").compareTo(point.cashFlow()));
        assertEquals(0, bd("-400").compareTo(point.cumulativeCashFlow()));
        assertEquals(0, bd("100").compareTo(point.producedVolume()));
        assertEquals(0, bd("90").compareTo(point.exportedVolume()));
    }

    @Test
    void ratiosWithoutInvestmentAreUnavailableInsteadOfDividingByZero() {
        var withInvestment = result("1", new IrrResult(IrrStatus.CALCULATED, bd("0.1")), recovered(1));
        var noInvestment = new EconomicResult(withInvestment.entries().stream().filter(e -> !e.id().equals("asset")).toList(),
                List.of(), withInvestment.periods(), List.of(), List.of(), bd("1"), withInvestment.indicators());
        var metrics = extractor.extract(source(deterministic(configuration("USD", 2030, 1, "0.1"), noInvestment, raw("1", "1")), List.of()));

        assertValue("0", metrics.metric(CAPEX));
        assertEquals(ScenarioMetricsExtractor.NO_INVESTMENT, metrics.metric(ROI).status());
        assertEquals(ScenarioMetricsExtractor.NO_INVESTMENT, metrics.metric(ROE).status());
        assertNull(metrics.metric(ROI).value());
    }

    @Test
    void missingSavedDataStaysUnavailableAndIsNeverReplacedByZero() {
        var metrics = extractor.extract(source(deterministic(configuration("USD", 2030, 1, "0.1"), null, null), List.of()));

        for (var metric : List.of(NPV, IRR, CAPEX, OPEX, NET_CASH_FLOW, ROI, PRODUCED_VOLUME, EXPORTED_VOLUME)) {
            assertNull(metrics.metric(metric).value(), metric.name());
            assertEquals(MetricValueDTO.NOT_AVAILABLE, metrics.metric(metric).status(), metric.name());
        }
        assertTrue(metrics.incomplete());
        assertTrue(metrics.annual().stream().allMatch(p -> p.cashFlow() == null && p.cumulativeCashFlow() == null));
    }

    @Test
    void probabilisticEvaluationUsesMeansOverRoundsAndKeepsConditionalDenominators() {
        var first = new EconomicRoundResult(1, "1",
                raw("100", "90"), result("100", new IrrResult(IrrStatus.CALCULATED, bd("0.10")), recovered(1)));
        var second = new EconomicRoundResult(2, "2",
                raw("300", "270"), result("300", new IrrResult(IrrStatus.NO_SIGN_CHANGE, null), notRecovered()));
        var metrics = extractor.extract(source(probabilistic(configuration("USD", 2030, 1, "0.1")), List.of(first, second)));

        assertEquals(EvaluationMode.PROBABILISTIC, metrics.mode());
        assertEquals(2, metrics.rounds());
        assertValue("200", metrics.metric(NPV));
        assertEquals(2, metrics.metric(NPV).availableRounds());
        assertNotNull(metrics.metric(NPV).distribution());
        assertEquals(0, bd("100").compareTo(metrics.metric(NPV).distribution().min()));
        // IRR exists in one round only: the mean is conditional and says so.
        assertValue("0.10", metrics.metric(IRR));
        assertEquals(1, metrics.metric(IRR).availableRounds());
        assertEquals(2, metrics.metric(IRR).totalRounds());
        assertValue("200", metrics.metric(PRODUCED_VOLUME));
        assertEquals(0, bd("200").compareTo(metrics.nodes().get(WELL_IDENTITY.toString()).flows().output()));
    }

    @Test
    void probabilisticEvaluationWithoutRoundsIsReportedAsIncomplete() {
        var metrics = extractor.extract(source(probabilistic(configuration("USD", 2030, 1, "0.1")), List.of()));

        assertEquals(0, metrics.rounds());
        assertEquals(MetricValueDTO.NOT_AVAILABLE, metrics.metric(NPV).status());
        assertTrue(metrics.incomplete());
    }
}
