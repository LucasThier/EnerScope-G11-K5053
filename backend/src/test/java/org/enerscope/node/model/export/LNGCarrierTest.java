package org.enerscope.node.model.export;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeIdentity;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.MockitoAnnotations.openMocks;

class LNGCarrierTest {

    @Mock
    private InvestmentCost mockInvestmentCost;

    @Mock
    private NodeGraphData mockNodeGraphData;

    @Mock
    private NodeIdentity mockNodeIdentity;

    @Mock
    private NodeTypeData mockNodeTypeData;

    private LNGCarrier lngCarrier;

    @BeforeEach
    void setUp() {
        openMocks(this);
        lngCarrier = new LNGCarrier(
                "Test LNG Carrier",
                NodeStateEnum.RUNNING,
                Instant.now(),
                360,
                MoneyAmount.of(15000.0f),
                10,
                MoneyAmount.of(40000.0f),
                0.8f,
                mockInvestmentCost,
                mockNodeGraphData,
                mockNodeIdentity,
                mockNodeTypeData,
                12,
                25000.0f,
                48.0f,
                MoneyAmount.of(80000.0f),
                5
        );
    }

    @Test
    void lngCarrierShouldHaveCorrectName() {
        assertEquals("Test LNG Carrier", lngCarrier.getName());
    }

    @Test
    void lngCarrierShouldHaveCorrectState() {
        assertEquals(NodeStateEnum.RUNNING, lngCarrier.getState());
    }

    @Test
    void lngCarrierShouldHaveCorrectShipCapacity() {
        assertEquals(25000.0f, lngCarrier.getShipCapacity());
    }

    @Test
    void lngCarrierShouldHaveCorrectFullLoadTime() {
        assertEquals(48.0f, lngCarrier.getFullLoadTime());
    }

    @Test
    void lngCarrierShouldHaveCorrectHiringCost() {
        assertEquals(MoneyAmount.of(80000.0f), lngCarrier.getHiringCost());
    }

    @Test
    void lngCarrierShouldHaveCorrectTimeToDestination() {
        assertEquals(5, lngCarrier.getTimeToDestination());
    }
}