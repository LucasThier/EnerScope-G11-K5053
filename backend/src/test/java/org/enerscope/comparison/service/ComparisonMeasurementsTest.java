package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.util.*;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.enerscope.economic.dto.EvaluationDTO.Snapshot;
import org.enerscope.economic.service.*;
import org.enerscope.economic.model.EconomicEntry;
import org.enerscope.economic.model.results.EconomicResult;
import org.enerscope.economic.model.enums.TaxClassification;
import org.enerscope.economic.model.enums.CashClassification;
import org.enerscope.economic.model.enums.Direction;
import org.enerscope.simulator.AnnualNodeMetrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonMeasurementsTest {
    final ComparisonMeasurements measurements = new ComparisonMeasurements();
    final EconomicResult result = new EconomicEngine(new EconomicValidator(), new EconomicIndicatorsCalculator()).calculate(EconomicExample.configuration(), EconomicExample.metrics());
    final Cell cell = new Cell("base", UUID.randomUUID(), UUID.randomUUID(), List.of(EconomicExample.FIRST));
    Snapshot snapshot(boolean connected) throws Exception {
        var nodes = EconomicExample.MAPPER.readTree("[{\"id\":\"" + EconomicExample.FIRST + "\"},{\"id\":\"" + EconomicExample.SECOND + "\"}]");
        var edges = EconomicExample.MAPPER.readTree(connected ? "[{\"fromNodeId\":\"" + EconomicExample.FIRST + "\",\"toNodeId\":\"" + EconomicExample.SECOND + "\"}]" : "[]");
        return new Snapshot(2, EconomicExample.configuration(), nodes, edges, List.of(), result);
    }
    List<AnnualNodeMetrics> rows() {
        List<AnnualNodeMetrics> rows = new ArrayList<>();
        for (int p = 1; p <= 5; p++) rows.add(new AnnualNodeMetrics(EconomicExample.FIRST, p, null, BigDecimal.TEN, null, null, null, null));
        return rows;
    }

    @Test
    void extractsStoredNpvAndConvertsIrrToPercent() throws Exception {
        assertEquals(result.npv(), measurements.extract(Metric.NPV, snapshot(false), result, List.of(), cell).value());
        assertEquals(result.indicators().irr().rate().multiply(new BigDecimal("100")), measurements.extract(Metric.IRR, snapshot(false), result, List.of(), cell).value());
    }

    @Test
    void missingFinancialResultsNeverBecomeZero() throws Exception {
        assertNull(measurements.extract(Metric.NPV, snapshot(false), null, List.of(), cell).value());
        assertNull(measurements.extract(Metric.PAYBACK, snapshot(false), result.withIndicators(new EconomicIndicatorsCalculator().insufficientData()), List.of(), cell).value());
    }

    @Test
    void averageRequiresEveryRoundAndCompatibleUnits() {
        var good = new ComparisonEngine.Measurement(BigDecimal.TEN, "USD", null);
        assertEquals(BigDecimal.TEN, measurements.average(Metric.NPV, List.of(good, good)).value());
        assertNull(measurements.average(Metric.NPV, List.of(good, new ComparisonEngine.Measurement(null, "USD", "Missing"))).value());
        assertNull(measurements.average(Metric.NPV, List.of(good, new ComparisonEngine.Measurement(BigDecimal.TEN, "EUR", null))).value());
    }

    @Test
    void deliveryRejectsMissingPeriodsAndNodesInSeries() throws Exception {
        assertNull(measurements.extract(Metric.DELIVERED_VOLUME, snapshot(false), result, List.of(), cell).value());
        var serial = new Cell("base", cell.versionId(), cell.evaluationId(), List.of(EconomicExample.FIRST, EconomicExample.SECOND));
        assertTrue(measurements.extract(Metric.DELIVERED_VOLUME, snapshot(true), result, rows(), serial).issue().contains("serie"));
    }

    @Test
    void expensesSeparateInvestmentFromOperationsAndExcludeInternalEntries() throws Exception {
        var c = snapshot(false).configuration();
        var expectedInvestment = result.entries().stream().filter(e -> e.taxClassification() == TaxClassification.CAPITALIZABLE && c.boundary().contains(e.taxEntityId())).map(e -> e.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, expectedInvestment.compareTo(measurements.extract(Metric.INVESTMENT, snapshot(false), result, List.of(), cell).value()));
        var base = measurements.extract(Metric.OPERATING_COST, snapshot(false), result, List.of(), cell).value();
        assertNotNull(base); assertTrue(base.signum() >= 0);
        var entries = new ArrayList<>(result.entries());
        String owner = c.boundary().iterator().next();
        entries.add(new EconomicEntry("internal", null, owner, owner, "Internal", "rule", null,
            Direction.EXPENSE, TaxClassification.DEDUCTIBLE,
            CashClassification.CASH, java.time.LocalDate.of(c.startYear(), 1, 1), null, BigDecimal.TEN, c.currency(), BigDecimal.ONE));
        var withInternal = new EconomicResult(entries, result.operationalMetrics(), result.periods(), result.entityTaxes(), result.pendingBalances(), result.npv(), result.indicators());
        assertEquals(base, measurements.extract(Metric.OPERATING_COST, snapshot(false), withInternal, List.of(), cell).value());
    }

    @Test
    void deliveryUsesObservedConversionsAndRejectsDuplicatePeriods() throws Exception {
        var snap = snapshot(false);
        BigDecimal factor = snap.configuration().conversions().stream().filter(v -> v.nodeId().equals(EconomicExample.FIRST) && v.metric() == org.enerscope.economic.model.enums.Driver.OUTPUT_VOLUME).findFirst().orElseThrow().factor();
        assertEquals(0, new BigDecimal("50").multiply(factor).compareTo(measurements.extract(Metric.DELIVERED_VOLUME, snap, result, rows(), cell).value()));
        var duplicate = rows();
        duplicate.add(duplicate.getFirst());
        assertNull(measurements.extract(Metric.DELIVERED_VOLUME, snap, result, duplicate, cell).value());
    }

    @Test
    void deliveryRejectsStaticDriversAndForeignNodes() throws Exception {
        var snap = snapshot(false);
        var staticConfig = EconomicExample.mutate(c -> ((com.fasterxml.jackson.databind.node.ObjectNode)c.path("conversions").get(0)).put("annualQuantity", 100));
        var staticSnapshot = new Snapshot(2, staticConfig, snap.physicalNodes(), snap.physicalConnections(), rows(), result);
        assertNull(measurements.extract(Metric.DELIVERED_VOLUME, staticSnapshot, result, rows(), cell).value());
        assertNull(measurements.extract(Metric.DELIVERED_VOLUME, snap, result, rows(), new Cell("base", cell.versionId(), cell.evaluationId(), List.of(UUID.randomUUID()))).value());
    }
}
