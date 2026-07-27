package org.enerscope.node.model.transportation;

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

class PipelineTest {

    @Mock
    private InvestmentCost mockInvestmentCost;

    @Mock
    private NodeGraphData mockNodeGraphData;

    @Mock
    private NodeIdentity mockNodeIdentity;

    @Mock
    private NodeTypeData mockNodeTypeData;

    private Pipeline pipeline;

    @BeforeEach
    void setUp() {
        openMocks(this);
        pipeline = new Pipeline(
                "Test Pipeline",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                300,
                MoneyAmount.of(10000.0f),
                7,
                MoneyAmount.of(25000.0f),
                1.5f,
                mockInvestmentCost,
                mockNodeGraphData,
                mockNodeIdentity,
                mockNodeTypeData,
                500.0f,
                15.5f,
                0.02f
        );
    }

    @Test
    void pipelineShouldHaveCorrectName() {
        assertEquals("Test Pipeline", pipeline.getName());
    }

    @Test
    void pipelineShouldHaveCorrectState() {
        assertEquals(NodeStateEnum.PROPOSED, pipeline.getState());
    }

    @Test
    void pipelineShouldHaveCorrectMaxFlowCapacity() {
        assertEquals(500.0f, pipeline.getMaxFlowCapacity());
    }

    @Test
    void pipelineShouldHaveCorrectLength() {
        assertEquals(15.5f, pipeline.getLength());
    }

    @Test
    void pipelineShouldHaveCorrectLossPerKm() {
        assertEquals(0.02f, pipeline.getLossPerKm());
    }

    @Test
    void pipelineShouldHaveCorrectLifespanInMonths() {
        assertEquals(300, pipeline.getLifespanInMonths());
    }
}