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

class PipelineConnectionTest {

    @Mock
    private InvestmentCost mockInvestmentCost;

    @Mock
    private NodeGraphData mockNodeGraphData;

    @Mock
    private NodeIdentity mockNodeIdentity;

    @Mock
    private NodeTypeData mockNodeTypeData;

    private PipelineConnection pipelineConnection;

    @BeforeEach
    void setUp() {
        openMocks(this);
        pipelineConnection = new PipelineConnection(
                "Test Connection",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                200,
                MoneyAmount.of(2000.0f),
                4,
                MoneyAmount.of(5000.0f),
                0.5f,
                mockInvestmentCost,
                mockNodeGraphData,
                mockNodeIdentity,
                mockNodeTypeData,
                300.0f,
                0.8f
        );
    }

    @Test
    void pipelineConnectionShouldHaveCorrectName() {
        assertEquals("Test Connection", pipelineConnection.getName());
    }

    @Test
    void pipelineConnectionShouldHaveCorrectState() {
        assertEquals(NodeStateEnum.PROPOSED, pipelineConnection.getState());
    }

    @Test
    void pipelineConnectionShouldHaveCorrectTransferCapacity() {
        assertEquals(300.0f, pipelineConnection.getTransferCapacity());
    }

    @Test
    void pipelineConnectionShouldHaveCorrectOutputPriority() {
        assertEquals(0.8f, pipelineConnection.getOutputPriority());
    }

    @Test
    void pipelineConnectionShouldHaveCorrectOperatingCosts() {
        assertEquals(MoneyAmount.of(5000.0f), pipelineConnection.getOperatingCosts());
    }
}