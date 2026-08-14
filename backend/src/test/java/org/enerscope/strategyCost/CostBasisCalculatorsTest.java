package org.enerscope.strategyCost;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostBasisCalculatorsTest {

    private MoneyAmount mockMoney;

    @BeforeEach
    void setUp() {
        mockMoney = mock(MoneyAmount.class);
    }

    // Tests para Flat
    @Test
    void Flat_ReturnsSameMoneyAmount() {
        Flat flat = new Flat();
        BaseNode mockNode = mock(BaseNode.class);

        MoneyAmount result = flat.CalculateCost(mockNode, mockMoney);

        assertEquals(mockMoney, result);
    }

    // Tests para Per_M
    @Test
    void Per_M_CalculatesCostForGatheringNetwork() {
        Per_M perM = new Per_M();
        GatheringNetwork mockNetwork = mock(GatheringNetwork.class);
        MoneyAmount expectedResult = mock(MoneyAmount.class);

        when(mockNetwork.getLength()).thenReturn(150.0f);
        when(mockMoney.multiply(150.0f)).thenReturn(expectedResult);

        MoneyAmount result = perM.CalculateCost(mockNetwork, mockMoney);

        assertEquals(expectedResult, result);
    }

    @Test
    void Per_M_ThrowsException_ForInvalidNodeType() {
        Per_M perM = new Per_M();
        BaseNode wrongNode = mock(BaseNode.class); // No es GatheringNetwork

        assertThrows(ClassCastException.class, () -> {
            perM.CalculateCost(wrongNode, mockMoney);
        });
    }

    // Tests para Per_KM
    @Test
    void Per_KM_CalculatesCostForPipeline() {
        Per_KM perKm = new Per_KM();
        Pipeline mockPipeline = mock(Pipeline.class);
        MoneyAmount expectedResult = mock(MoneyAmount.class);

        when(mockPipeline.getLength()).thenReturn(12.5f);
        when(mockMoney.multiply(12.5f)).thenReturn(expectedResult);

        MoneyAmount result = perKm.CalculateCost(mockPipeline, mockMoney);

        assertEquals(expectedResult, result);
    }

    @Test
    void Per_KM_ThrowsException_ForInvalidNodeType() {
        Per_KM perKm = new Per_KM();
        BaseNode wrongNode = mock(BaseNode.class);

        assertThrows(ClassCastException.class, () -> {
            perKm.CalculateCost(wrongNode, mockMoney);
        });
    }

    // Tests para Per_KM2
    @Test
    void Per_KM2_CalculatesCostForWell() {
        Per_KM2 perKm2 = new Per_KM2();
        Well mockWell = mock(Well.class);
        MoneyAmount expectedResult = mock(MoneyAmount.class);

        when(mockWell.getSurface()).thenReturn(5.0f);
        when(mockMoney.multiply(5.0f)).thenReturn(expectedResult);

        MoneyAmount result = perKm2.CalculateCost(mockWell, mockMoney);

        assertEquals(expectedResult, result);
    }

    @Test
    void Per_KM2_ThrowsException_ForInvalidNodeType() {
        Per_KM2 perKm2 = new Per_KM2();
        BaseNode wrongNode = mock(BaseNode.class);

        assertThrows(ClassCastException.class, () -> {
            perKm2.CalculateCost(wrongNode, mockMoney);
        });
    }

    // Tests para Per_Conections_Total
    @Test
    void Per_Conections_Total_CalculatesCostForGatheringNetwork() {
        Per_Conections_Total perConnections = new Per_Conections_Total();
        GatheringNetwork mockNetwork = mock(GatheringNetwork.class);
        MoneyAmount expectedResult = mock(MoneyAmount.class);

        when(mockNetwork.getConnectedWells()).thenReturn(10);
        when(mockMoney.multiply(10)).thenReturn(expectedResult);

        MoneyAmount result = perConnections.CalculateCost(mockNetwork, mockMoney);

        assertEquals(expectedResult, result);
    }

    @Test
    void Per_Conections_Total_ReturnsSameMoneyForPipelineConnection() {
        Per_Conections_Total perConnections = new Per_Conections_Total();
        PipelineConnection mockConnection = mock(PipelineConnection.class);

        MoneyAmount result = perConnections.CalculateCost(mockConnection, mockMoney);

        assertEquals(mockMoney, result);
    }

    @Test
    void Per_Conections_Total_ThrowsException_ForInvalidNodeType() {
        Per_Conections_Total perConnections = new Per_Conections_Total();
        BaseNode wrongNode = mock(BaseNode.class);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            perConnections.CalculateCost(wrongNode, mockMoney);
        });

        assertEquals("Wrong type of node", exception.getMessage());
    }
}
