package org.enerscope.node.model.extraction;

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

class WellTest {

    @Mock
    private InvestmentCost mockInvestmentCost;

    @Mock
    private NodeGraphData mockNodeGraphData;

    @Mock
    private NodeIdentity mockNodeIdentity;

    @Mock
    private NodeTypeData mockNodeTypeData;

    private Well well;

    @BeforeEach
    void setUp() {
        openMocks(this);
        well = new Well(
                "Test Well",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                240,
                MoneyAmount.of(5000.0f),
                30,
                MoneyAmount.of(15000.0f),
                2.5f,
                mockInvestmentCost,
                mockNodeGraphData,
                mockNodeIdentity,
                mockNodeTypeData,
                1000.0f,
                0.05f,
                0.85f,
                12,
                MoneyAmount.of(25000.0f)
        );
    }

    @Test
    void wellShouldHaveCorrectName() {
        assertEquals("Test Well", well.getName());
    }

    @Test
    void wellShouldHaveCorrectState() {
        assertEquals(NodeStateEnum.PROPOSED, well.getState());
    }

    @Test
    void wellShouldHaveCorrectMaxCollectionCapacity() {
        assertEquals(1000.0f, well.getMaxCollectionCapacity());
    }

    @Test
    void wellShouldHaveCorrectDeclineCurve() {
        assertEquals(0.05f, well.getDeclineCurve());
    }

    @Test
    void wellShouldHaveCorrectGasRichness() {
        assertEquals(0.85f, well.getGasRichness());
    }

    @Test
    void wellShouldHaveCorrectDTMTime() {
        assertEquals(12, well.getDTMTime());
    }

    @Test
    void wellShouldHaveCorrectDTMCost() {
        assertEquals(MoneyAmount.of(25000.0f), well.getDTMCost());
    }
}