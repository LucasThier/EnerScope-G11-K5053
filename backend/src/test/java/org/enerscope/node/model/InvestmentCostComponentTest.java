package org.enerscope.node.model;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.extraction.Well;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvestmentCostComponentTest {

    @Test
    void aFlatComponentCostsItsAmount() {
        // FLAT fell through to the switch's default and was charged as zero.
        InvestmentCostComponent component =
                new InvestmentCostComponent("Licencia", MoneyAmount.of(2500), CostBasisEnum.FLAT);

        assertEquals(MoneyAmount.of(2500).value(), component.CalculateCost(new Well()).value());
    }

    @Test
    void aBasisWithNoCalculatorCostsNothing() {
        InvestmentCostComponent component =
                new InvestmentCostComponent("Entradas", MoneyAmount.of(2500), CostBasisEnum.PER_CONECTIONS_INPUT);

        assertEquals(MoneyAmount.of(0).value(), component.CalculateCost(new Well()).value());
    }
}
