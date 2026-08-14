package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class CostTest {
    private static class DummyNode extends BaseNode {
        public void setLifespanInMonths(int months) {
            this.lifespanInMonths = months;
        }
        public void setMaintenanceIntervalInDays(int days) {
            this.maintenanceIntervalInDays = days;
        }
        public void setUpkeepCosts(MoneyAmount amount) {
            this.upkeepCosts = amount;
        }
        public void setOperatingCosts(MoneyAmount amount) {
            this.operatingCosts = amount;
        }
        public void setInvestmentCost(InvestmentCost cost) {
            this.investmentCost = cost;
        }
    }

    private DummyNode baseNode;
    private MoneyAmount mockMoneyAmount;

    @BeforeEach
    void setUp() {
        baseNode = new DummyNode();
        mockMoneyAmount = mock(MoneyAmount.class);
    }

    // Tests para CalculateInvestmentCost

    @Test
    void CalculateInvestmentCost_Success() {
        InvestmentCost mockInvestment = mock(InvestmentCost.class);
        MoneyAmount expectedCost = MoneyAmount.of(1000);

        when(mockInvestment.CalculateCost(baseNode)).thenReturn(expectedCost);
        baseNode.setInvestmentCost(mockInvestment);

        MoneyAmount actualCost = baseNode.CalculateInvestmentCost();
        assertEquals(expectedCost, actualCost);
    }

    @Test
    void CalculateInvestmentCost_ThrowsException_WhenNull() {
        baseNode.setInvestmentCost(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            baseNode.CalculateInvestmentCost();
        });
        assertEquals("Investment Cost is empty", exception.getMessage());
    }

    // Tests para CalculateOperatingCost

    @Test
    void CalculateOperatingCost_Success() {
        baseNode.setLifespanInMonths(12);
        MoneyAmount operatingCost = mock(MoneyAmount.class);
        MoneyAmount expectedTotal = mock(MoneyAmount.class);

        when(operatingCost.multiply(12)).thenReturn(expectedTotal);
        baseNode.setOperatingCosts(operatingCost);

        MoneyAmount result = baseNode.CalculateOperatingCost();
        assertEquals(expectedTotal, result);
    }

    @Test
    void CalculateOperatingCost_ThrowsException_WhenNull() {
        baseNode.setOperatingCosts(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            baseNode.CalculateOperatingCost();
        });
        assertEquals("Base Node missing arguments", exception.getMessage());
    }

    // Tests para CalculateUpkeepCost

    @Test
    void CalculateUpkeepCost_Success() {
        // Lifespan = 12 meses, Intervalo = 60 días
        // Cálculo interno: 12 / (60 / 30) = 12 / 2 = 6 mantenimientos totales.
        baseNode.setLifespanInMonths(12);
        baseNode.setMaintenanceIntervalInDays(60);

        MoneyAmount upkeepCost = mock(MoneyAmount.class);
        MoneyAmount expectedTotal = mock(MoneyAmount.class);

        when(upkeepCost.multiply(6)).thenReturn(expectedTotal);
        baseNode.setUpkeepCosts(upkeepCost);

        MoneyAmount result = baseNode.CalculateUpkeepCost();
        assertEquals(expectedTotal, result);
    }

    @Test
    void CalculateUpkeepCost_ThrowsException_WhenNull() {
        baseNode.setUpkeepCosts(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            baseNode.CalculateUpkeepCost();
        });
        assertEquals("Base Node missing arguments", exception.getMessage());
    }

    // Tests para CalculateTotalCost

    @Test
    void CalculateTotalCost_Success() {

        InvestmentCost mockInvestment = mock(InvestmentCost.class);
        MoneyAmount invCost = mock(MoneyAmount.class);
        when(mockInvestment.CalculateCost(baseNode)).thenReturn(invCost);
        baseNode.setInvestmentCost(mockInvestment);

        MoneyAmount opCost = mock(MoneyAmount.class);
        when(opCost.multiply(anyInt())).thenReturn(mock(MoneyAmount.class));
        baseNode.setOperatingCosts(opCost);
        baseNode.setLifespanInMonths(12);

        MoneyAmount upkCost = mock(MoneyAmount.class);
        when(upkCost.multiply(anyInt())).thenReturn(mock(MoneyAmount.class));
        baseNode.setUpkeepCosts(upkCost);
        baseNode.setMaintenanceIntervalInDays(30);

        MoneyAmount intermediateSum = mock(MoneyAmount.class);
        MoneyAmount finalSum = mock(MoneyAmount.class);
        when(invCost.add(any())).thenReturn(intermediateSum);
        when(intermediateSum.add(any())).thenReturn(finalSum);

        MoneyAmount total = baseNode.CalculateTotalCost();

        assertNotNull(total);
        assertEquals(finalSum, total);
    }

    @Test
    void CalculateTotalCost_HandlesExceptionsAndReturnsZeroForMissingCosts() {

        baseNode.setInvestmentCost(null);
        baseNode.setOperatingCosts(null);
        baseNode.setUpkeepCosts(null);

        try (var mockedStatic = mockStatic(MoneyAmount.class)) {
            MoneyAmount zeroAmount = mock(MoneyAmount.class);
            mockedStatic.when(() -> MoneyAmount.of(0)).thenReturn(zeroAmount);

            when(zeroAmount.add(zeroAmount)).thenReturn(zeroAmount);

            MoneyAmount total = baseNode.CalculateTotalCost();

            assertEquals(zeroAmount, total);
        }
    }
}
