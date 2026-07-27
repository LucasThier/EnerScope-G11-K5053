package org.enerscope.node.model.liquefaction;

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

class FLNGUnitTest {

    @Mock
    private InvestmentCost mockInvestmentCost;

    @Mock
    private NodeGraphData mockNodeGraphData;

    @Mock
    private NodeIdentity mockNodeIdentity;

    @Mock
    private NodeTypeData mockNodeTypeData;

    private FLNGUnit flngUnit;

    @BeforeEach
    void setUp() {
        openMocks(this);
        flngUnit = new FLNGUnit(
                "Test FLNG Unit",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                240,
                MoneyAmount.of(8000.0f),
                30,
                MoneyAmount.of(20000.0f),
                1.0f,
                mockInvestmentCost,
                mockNodeGraphData,
                mockNodeIdentity,
                mockNodeTypeData,
                150.0f,
                1.2f,
                500.0f,
                10.0f,
                MoneyAmount.of(100000.0f)
        );
    }

    @Test
    void flngUnitShouldHaveCorrectName() {
        assertEquals("Test FLNG Unit", flngUnit.getName());
    }

    @Test
    void flngUnitShouldHaveCorrectState() {
        assertEquals(NodeStateEnum.PROPOSED, flngUnit.getState());
    }

    @Test
    void flngUnitShouldHaveCorrectMaxProcessingCapacity() {
        assertEquals(150.0f, flngUnit.getMaxProcessingCapacity());
    }

    @Test
    void flngUnitShouldHaveCorrectMTPARatio() {
        assertEquals(1.2f, flngUnit.getMTPARatio());
    }

    @Test
    void flngUnitShouldHaveCorrectVesselDepth() {
        assertEquals(10.0f, flngUnit.getVesselDepth());
    }

    @Test
    void flngUnitShouldHaveCorrectHiringCost() {
        assertEquals(MoneyAmount.of(100000.0f), flngUnit.getHiringCost());
    }
}