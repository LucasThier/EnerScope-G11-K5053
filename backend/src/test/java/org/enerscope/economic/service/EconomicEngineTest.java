package org.enerscope.economic.service;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.node.*;
import org.enerscope.economic.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.enerscope.economic.service.EconomicExample.*;

class EconomicEngineTest {
    private final EconomicEngine engine = new EconomicEngine(new EconomicValidator());
    private EconomicResult run(EconomicConfiguration c) { return engine.calculate(c, metrics()); }
    private void amount(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual); }

    @Test void completeExampleReconcilesEverySectionAndNpv() {
        var r = run(configuration()); amount("169.20", r.npv());
        String[][] expected = {
            {"0","0","0","0","0","0","20","1100","0","0","-1080"},
            {"100","70","200","-170","0","-170","0","5","200","0","25"},
            {"600","120","200","280","33","247","0","5","200","-60","382"},
            {"600","120","200","280","84","196","0","5","200","60","451"},
            {"600","120","200","280","84","196","0","5","200","0","391"},
            {"600","120","200","280","84","196","100","5","200","0","491"}};
        for (int t=0; t<expected.length; t++) {
            var p=r.periods().get(t);
            var values=List.of(p.taxableIncome(),p.deductibleExpenses(),p.nonCashExpenses(),p.resultBeforeTax(),p.taxes(),
                    p.resultAfterTax(),p.nonTaxableIncome(),p.nonTaxableExpenses(),p.nonCashAdjustments(),p.cashTimingAdjustment(),p.cashFlow());
            for(int j=0;j<values.size();j++) amount(expected[t][j],values.get(j));
        }
        assertTrue(r.pendingBalances().isEmpty());
        assertEquals(r.entries().size(), r.entries().stream().map(EconomicEntry::id).distinct().count());
    }
    @Test void internalTransfersRemainTaxablePerEntityButDisappearOnConsolidation() {
        var r=run(configuration());
        var a=r.entityTaxes().stream().filter(t->t.taxEntityId().equals("A") && t.year()==2032).findFirst().orElseThrow();
        var b=r.entityTaxes().stream().filter(t->t.taxEntityId().equals("B") && t.year()==2032).findFirst().orElseThrow();
        amount("31.80",a.tax()); amount("1.20",b.tax()); amount("82",a.lossesUsed()); amount("88",b.lossesUsed());
        assertEquals(10,r.entries().stream().filter(e->e.counterpartyId()!=null).count());
    }
    @Test void partialBoundaryRetainsTransferWithExcludedCounterparty() {
        var r=run(mutate(t->t.putArray("boundary").add("A")));
        amount("80",r.periods().get(1).taxableIncome());
        amount("35",r.periods().get(1).cashFlow());
    }
    @Test void zeroProductionPreservesFixedCosts() {
        var zero=metrics().stream().map(m->new OperationalMetric(m.nodeId(),m.year(),m.metric(),m.unit(),BigDecimal.ZERO)).toList();
        var r=engine.calculate(configuration(),zero);
        amount("60",r.periods().get(1).deductibleExpenses()); amount("-65",r.periods().get(1).cashFlow());
    }
    @Test void zeroWaccEqualsSumOfCashFlows() {
        amount("660",run(mutate(t->t.put("wacc",0))).npv());
    }
    @Test void partialDeductibilityMovesCashRemainderToEnai() {
        var r=run(mutate(t->t.withArray("taxTreatments").forEach(n->{if(n.get("concept").asText().equals("fixed"))((ObjectNode)n).put("deductibleFraction",.5);} )));
        amount("45",r.periods().get(1).deductibleExpenses()); amount("30",r.periods().get(1).nonTaxableExpenses());
    }
    @Test void nonCashEntriesNeverHavePaymentDates() {
        assertTrue(run(configuration()).entries().stream().filter(e->e.sourceRuleId().startsWith("depreciation:")).allMatch(e->e.cashDate()==null));
    }
    @Test void monthlyDepreciationRespectsServiceDateAndResidual() {
        var r=run(mutate(t->{var a=(ObjectNode)t.withArray("assets").get(0); a.put("serviceDate","2031-07-01");a.put("residualValue",100);}));
        amount("90",r.periods().get(1).nonCashExpenses());amount("180",r.periods().get(2).nonCashExpenses());
    }
    @Test void taxPaymentLagCreatesCashAdjustmentAndPendingTax() {
        var r=run(mutate(t->t.withArray("taxEntities").forEach(n->((ObjectNode)n).put("taxPaymentLagYears",1))));
        amount("415",r.periods().get(2).cashFlow());
        amount("33",r.periods().get(2).taxes());
        amount("84",r.pendingBalances().stream().map(p->p.amount()).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void receivableBeyondHorizonIsReportedWithoutTerminalRecovery() {
        var r=run(mutate(t->{var rule=(ObjectNode)t.withArray("contracts").get(0).get("revenueRule");
            ((ObjectNode)rule.withArray("occurrences").get(3)).put("cashDate","2036-12-31");}));
        amount("391",r.periods().get(5).cashFlow());
        amount("100",r.pendingBalances().stream().map(p->p.amount()).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void lossOffsetLimitPreservesUnusedLosses() {
        var r=run(mutate(t->t.withArray("taxEntities").forEach(n->((ObjectNode)n).put("lossOffsetLimit",.1))));
        amount("75.60",r.periods().get(2).taxes());
    }
    @Test void expiredOpeningLossDoesNotReduceFutureTax() {
        var r=run(mutate(t->t.withArray("taxEntities").forEach(n->{((ObjectNode)n).put("openingLoss",100);((ObjectNode)n).put("lossExpiryYears",1);} )));
        amount("200",r.entityTaxes().stream().filter(t->t.year()==2031).map(t->t.lossesExpired()).reduce(BigDecimal.ZERO,BigDecimal::add));
        amount("33",r.periods().get(2).taxes());
    }
    @Test void invalidCurrencyOwnershipAndUnitsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->run(mutate(t->((ObjectNode)t.withArray("assets").get(0)).put("currency","EUR"))));
        assertThrows(IllegalArgumentException.class,()->run(mutate(t->((ObjectNode)t.withArray("nodeProfiles").get(0).get("ownership").get(0)).put("share",.7))));
        assertThrows(IllegalArgumentException.class,()->run(mutate(t->((ObjectNode)t.withArray("conversions").get(0)).put("unit","OTHER"))));
    }
    @Test void missingMetricsAndDuplicateMetricsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->engine.calculate(configuration(),List.of()));
        var duplicated=new ArrayList<>(metrics());duplicated.add(duplicated.getFirst());
        assertThrows(IllegalArgumentException.class,()->engine.calculate(configuration(),duplicated));
    }
    @Test void capexCannotBeDeductedTwice() {
        assertThrows(IllegalArgumentException.class,()->run(mutate(t->t.withArray("taxTreatments").forEach(n->{
            if(n.get("concept").asText().equals("equipment")){((ObjectNode)n).put("classification","DEDUCTIBLE");((ObjectNode)n).put("deductibleFraction",1);}}))));
    }
    @Test void overlappingTreatmentsAndInvalidDatesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->run(mutate(t->t.withArray("taxTreatments").add(t.withArray("taxTreatments").get(0).deepCopy()))));
        assertThrows(IllegalArgumentException.class,()->run(mutate(t->((ObjectNode)t.withArray("adjustments").get(0).get("occurrences").get(0)).put("recognitionDate","2029-01-01"))));
    }
    @Test void evaluationsAreDeterministic() { assertEquals(run(configuration()),run(configuration())); }

    @Test void staticDriversUseExplicitQuantitiesAndRates() {
        for (String driver:List.of("INSTALLED_CAPACITY","DISTANCE","CONNECTION_COUNT","PERCENT_CAPEX")) {
            var c=mutate(t->{
                var rule=(ObjectNode)t.withArray("nodeProfiles").get(0).get("rules").get(1);
                rule.put("driver",driver);rule.put("unitValue",.5);
                var conversion=t.withArray("conversions").addObject();conversion.put("nodeId",FIRST.toString());
                conversion.put("metric",driver);conversion.put("rawUnit","UNIT");conversion.put("unit","UNIT");conversion.put("factor",1);conversion.put("annualQuantity",100);
            });
            var metrics=new ArrayList<>(metrics());for(int year=2031;year<=2035;year++)
                metrics.add(new OperationalMetric(FIRST,year,org.enerscope.economic.model.EconomicConfiguration.Driver.valueOf(driver),"UNIT",new BigDecimal("100")));
            amount("169.20",engine.calculate(c,metrics).npv());
        }
    }
    @Test void emptyYearsRemainInDiscountTimeline() {
        var c=mutate(t->{t.putArray("nodeProfiles");t.putArray("assets");t.putArray("adjustments");t.putArray("contracts");t.putArray("conversions");});
        var r=engine.calculate(c,List.of());assertEquals(6,r.periods().size());amount("0",r.npv());
    }
    @Test void nonCashTaxableIncomeIsReversedInCashFlow() {
        var c=mutate(t->{var rule=(ObjectNode)t.withArray("nodeProfiles").get(0).get("rules").get(6);
            rule.put("cashClassification","NON_CASH");rule.withArray("occurrences").forEach(n->((ObjectNode)n).putNull("cashDate"));
            t.withArray("taxTreatments").forEach(n->{if(n.get("concept").asText().equals("grant"))((ObjectNode)n).put("classification","TAXABLE");});});
        var p=run(c).periods().getFirst();amount("20",p.taxableIncome());amount("-20",p.nonCashAdjustments());amount("-1106",p.cashFlow());
    }
    @Test void disabledLossCarryForwardDoesNotRefundOrOffset() {
        var r=run(mutate(t->t.withArray("taxEntities").forEach(n->((ObjectNode)n).put("carryLosses",false))));
        amount("0",r.periods().get(1).taxes());amount("84",r.periods().get(2).taxes());
    }
}
