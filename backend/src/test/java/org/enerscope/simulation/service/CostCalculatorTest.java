package org.enerscope.simulation.service;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.transportation.Pipeline;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator();

    private static Well well(int lifespanMonths, int maintenanceIntervalDays) {
        Well well = new Well();
        well.setName("Pozo");
        well.setLifespanInMonths(lifespanMonths);
        well.setMaintenanceIntervalInDays(maintenanceIntervalDays);
        well.setOperatingCosts(MoneyAmount.of(1000));
        well.setUpkeepCosts(MoneyAmount.of(500));
        return well;
    }

    private static InvestmentCost investment(InvestmentCostComponent... components) {
        return new InvestmentCost(new ArrayList<>(List.of(components)));
    }

    @Test
    void opexCoversOnlyTheDaysInsideTheHorizon() {
        // 240 months outlive a 10-year horizon: 3650 days, i.e. 3650 / 30 months.
        CostCalculator.NodeCost cost = calculator.costOf(well(240, 0), 10);

        assertEquals(new BigDecimal("121666.67"), cost.opex());
    }

    @Test
    void opexStopsWhenTheLifespanEnds() {
        // 24 months = 720 days, well inside the horizon.
        CostCalculator.NodeCost cost = calculator.costOf(well(24, 0), 10);

        assertEquals(new BigDecimal("24000.00"), cost.opex());
    }

    @Test
    void aNodeWithNoLifespanCostsNothingToRun() {
        // The simulator stops such a node at hour zero, so it must not be charged either.
        CostCalculator.NodeCost cost = calculator.costOf(well(0, 30), 10);

        assertEquals(new BigDecimal("0.00"), cost.opex());
        assertEquals(new BigDecimal("0.00"), cost.upkeep());
    }

    @Test
    void upkeepChargesEachMaintenanceStopInsideTheOperatingTime() {
        // 3650 operating days / 365-day interval = 10 stops of 500.
        CostCalculator.NodeCost cost = calculator.costOf(well(240, 365), 10);

        assertEquals(new BigDecimal("5000.00"), cost.upkeep());
    }

    @Test
    void noMaintenanceIntervalMeansNoUpkeep() {
        CostCalculator.NodeCost cost = calculator.costOf(well(240, 0), 10);

        assertEquals(new BigDecimal("0.00"), cost.upkeep());
    }

    @Test
    void capexAddsTheInvestmentComponentsOnceWhateverTheHorizon() {
        Well well = well(240, 0);
        well.setSurface(2f);
        well.setInvestmentCost(investment(
                new InvestmentCostComponent("Perforación", MoneyAmount.of(1000), CostBasisEnum.FLAT),
                new InvestmentCostComponent("Superficie", MoneyAmount.of(100), CostBasisEnum.PER_KM2)));

        assertEquals(new BigDecimal("1200.00"), calculator.costOf(well, 1).capex());
        assertEquals(new BigDecimal("1200.00"), calculator.costOf(well, 30).capex());
    }

    @Test
    void aComponentThatDoesNotApplyToTheNodeAddsNothing() {
        // PER_KM prices a pipeline by its length; on a well it cannot be computed.
        Well well = well(240, 0);
        well.setInvestmentCost(investment(
                new InvestmentCostComponent("Tramo", MoneyAmount.of(100), CostBasisEnum.PER_KM)));

        assertEquals(new BigDecimal("0.00"), calculator.costOf(well, 10).capex());
    }

    @Test
    void aNodeWithoutInvestmentCostHasNoCapex() {
        Well well = well(240, 0);
        well.setInvestmentCost(null);

        assertEquals(new BigDecimal("0.00"), calculator.costOf(well, 10).capex());
    }

    @Test
    void totalIsCapexPlusOpexPlusUpkeep() {
        Pipeline pipeline = new Pipeline();
        pipeline.setLifespanInMonths(12);
        pipeline.setMaintenanceIntervalInDays(180);
        pipeline.setOperatingCosts(MoneyAmount.of(10));
        pipeline.setUpkeepCosts(MoneyAmount.of(5));
        pipeline.setLength(3f);
        pipeline.setInvestmentCost(investment(
                new InvestmentCostComponent("Ducto", MoneyAmount.of(100), CostBasisEnum.PER_KM)));

        CostCalculator.NodeCost cost = calculator.costOf(pipeline, 5);

        // capex 300, opex 360 days → 12 months × 10 = 120, upkeep 360 / 180 = 2 stops × 5 = 10
        assertEquals(new BigDecimal("300.00"), cost.capex());
        assertEquals(new BigDecimal("120.00"), cost.opex());
        assertEquals(new BigDecimal("10.00"), cost.upkeep());
        assertEquals(new BigDecimal("430.00"), cost.total());
    }

    @Test
    void costOfAListCostsEveryNode() {
        List<CostCalculator.NodeCost> costs = calculator.costOf(List.of(well(240, 0), well(24, 0)), 10);

        assertEquals(2, costs.size());
        assertEquals(new BigDecimal("24000.00"), costs.get(1).opex());
    }

    @Test
    void rejectsAHorizonShorterThanOneYear() {
        assertThrows(IllegalArgumentException.class, () -> calculator.costOf(well(240, 0), 0));
    }
}
