package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestmentCostTest {

    private InvestmentCost investmentCost;
    private BaseNode mockBaseNode;

    @BeforeEach
    void setUp() {
        investmentCost = new InvestmentCost();
        mockBaseNode = mock(BaseNode.class);
    }

    private void setComponents(List<InvestmentCostComponent> components) throws Exception {
        Field field = InvestmentCost.class.getDeclaredField("components");
        field.setAccessible(true);
        field.set(investmentCost, components);
    }

    @Test
    void CalculateCost_SumsAllComponentsSuccessfully() throws Exception {
        InvestmentCostComponent comp1 = mock(InvestmentCostComponent.class);
        InvestmentCostComponent comp2 = mock(InvestmentCostComponent.class);

        MoneyAmount cost1 = mock(MoneyAmount.class);
        MoneyAmount cost2 = mock(MoneyAmount.class);

        when(comp1.CalculateCost(mockBaseNode)).thenReturn(cost1);
        when(comp2.CalculateCost(mockBaseNode)).thenReturn(cost2);

        setComponents(List.of(comp1, comp2));

        MoneyAmount zeroAmount = mock(MoneyAmount.class);
        MoneyAmount accumulatedAmount = mock(MoneyAmount.class);

        try (var mockedStatic = mockStatic(MoneyAmount.class)) {
            mockedStatic.when(() -> MoneyAmount.of(0)).thenReturn(zeroAmount);
            when(zeroAmount.addAll(List.of(cost1, cost2))).thenReturn(accumulatedAmount);

            MoneyAmount total = investmentCost.CalculateCost(mockBaseNode);

            assertEquals(accumulatedAmount, total);
            verify(comp1, times(1)).CalculateCost(mockBaseNode);
            verify(comp2, times(1)).CalculateCost(mockBaseNode);
        }
    }

    @Test
    void CalculateCost_HandlesComponentException() throws Exception {

        InvestmentCostComponent compSuccess = mock(InvestmentCostComponent.class);
        InvestmentCostComponent compFailing = mock(InvestmentCostComponent.class);

        MoneyAmount costSuccess = mock(MoneyAmount.class);
        MoneyAmount zeroAmount = mock(MoneyAmount.class);
        MoneyAmount expectedAccumulated = mock(MoneyAmount.class);

        when(compSuccess.CalculateCost(mockBaseNode)).thenReturn(costSuccess);
        when(compFailing.CalculateCost(mockBaseNode)).thenThrow(new RuntimeException("Error calculating component"));

        setComponents(List.of(compSuccess, compFailing));

        try (var mockedStatic = mockStatic(MoneyAmount.class)) {
            mockedStatic.when(() -> MoneyAmount.of(0)).thenReturn(zeroAmount);

            when(zeroAmount.addAll(anyList())).thenReturn(expectedAccumulated);

            MoneyAmount total = investmentCost.CalculateCost(mockBaseNode);

            assertNotNull(total);
            assertEquals(expectedAccumulated, total);
        }
    }
}
