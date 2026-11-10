package org.enerscope.economic.service;
import java.math.BigDecimal;
import java.util.*;
import org.enerscope.economic.model.results.*;
import org.enerscope.economic.model.enums.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.enerscope.economic.service.EconomicExample.*;
class EconomicStatisticsCalculatorTest {
    final EconomicStatisticsCalculator calculator=new EconomicStatisticsCalculator();
    final EconomicEngine engine=new EconomicEngine(new EconomicValidator(),new EconomicIndicatorsCalculator());
    @Test void nearestRankPercentilesDoNotInterpolateAndRetainTies() {
        var values=List.of(new BigDecimal("40"),BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("20"));
        var s=calculator.statistics(values);
        assertEquals(4,s.count());assertEquals(new BigDecimal("20"),s.mean());
        assertEquals(BigDecimal.TEN,s.p10());assertEquals(BigDecimal.TEN,s.p50());assertEquals(new BigDecimal("40"),s.p90());
    }
    @Test void emptyAndSingleObservationStatisticsAreExplicit() {
        var empty=calculator.statistics(List.of());assertEquals(0,empty.count());assertNull(empty.mean());
        var one=calculator.statistics(List.of(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO,one.mean());assertEquals(one.mean(),one.p90());
    }
    @Test void summaryUsesEachRoundsOwnIndicatorsAndShowsInvalidCounts() {
        var good=engine.calculate(configuration(),metrics());
        var missing=good.withIndicators(new EconomicIndicatorsCalculator().insufficientData());
        var summary=calculator.calculate(List.of(new EconomicRoundResult(1,"1",List.of(),good),
                new EconomicRoundResult(2,"2",List.of(),missing)));
        assertEquals(2,summary.rounds());assertEquals(1,summary.irr().count());
        assertEquals(1,summary.irrStatuses().get("INSUFFICIENT_DATA"));
        assertEquals(1,summary.simplePayback().denominator());
        assertEquals(BigDecimal.ONE,summary.simplePayback().recoveredProbability());
        assertEquals(BigDecimal.ONE,summary.positiveNpvProbability());
    }
    @Test void uncomputablePaybacksHaveNoInventedRecoveryProbability() {
        var result=engine.calculate(configuration(),metrics()).withIndicators(new EconomicIndicatorsCalculator().insufficientData());
        var summary=calculator.calculate(List.of(new EconomicRoundResult(1,"1",List.of(),result)));
        assertNull(summary.simplePayback().recoveredProbability());assertEquals(0,summary.simplePayback().denominator());
        assertEquals(0,summary.irr().count());assertNull(summary.irr().p50());
    }
    @Test void perRoundTaxationIsNotTaxationOfMeanProduction() {
        var low=metrics().stream().map(m->new org.enerscope.economic.model.OperationalMetric(m.nodeId(),m.year(),m.metric(),m.unit(),BigDecimal.ZERO)).toList();
        var high=metrics().stream().map(m->new org.enerscope.economic.model.OperationalMetric(m.nodeId(),m.year(),m.metric(),m.unit(),m.quantity().multiply(new BigDecimal("2")))).toList();
        var a=engine.calculate(configuration(),low); var b=engine.calculate(configuration(),high);
        var summary=calculator.calculate(List.of(new EconomicRoundResult(1,"1",List.of(),a),new EconomicRoundResult(2,"2",List.of(),b)));
        var averageInput=engine.calculate(configuration(),metrics());
        assertNotEquals(0,summary.npv().mean().compareTo(averageInput.npv()));
        assertEquals(0,summary.npv().mean().compareTo(a.npv().add(b.npv()).divide(new BigDecimal("2"))));
    }
}
