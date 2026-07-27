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

class SeaportTerminalTest {

    @Mock
    private InvestmentCost mockInvestmentCost;

    @Mock
    private NodeGraphData mockNodeGraphData;

    @Mock
    private NodeIdentity mockNodeIdentity;

    @Mock
    private NodeTypeData mockNodeTypeData;

    private SeaportTerminal seaportTerminal;

    @BeforeEach
    void setUp() {
        openMocks(this);
        seaportTerminal = new SeaportTerminal(
                "Test Seaport Terminal",
                NodeStateEnum.PENDING,
                Instant.now(),
                180,
                MoneyAmount.of(5000.0f),
                14,
                MoneyAmount.of(12000.0f),
                2.0f,
                mockInvestmentCost,
                mockNodeGraphData,
                mockNodeIdentity,
                mockNodeTypeData,
                1000.0f,
                12.5f,
                50
        );
    }

    @Test
    void seaportTerminalShouldHaveCorrectName() {
        assertEquals("Test Seaport Terminal", seaportTerminal.getName());
    }

    @Test
    void seaportTerminalShouldHaveCorrectState() {
        assertEquals(NodeStateEnum.PENDING, seaportTerminal.getState());
    }

    @Test
    void seaportTerminalShouldHaveCorrectIntermediateStorage() {
        assertEquals(1000.0f, seaportTerminal.getIntermediateStorage());
    }

    @Test
    void seaportTerminalShouldHaveCorrectPortDepth() {
        assertEquals(12.5f, seaportTerminal.getPortDepth());
    }

    @Test
    void seaportTerminalShouldHaveCorrectShipCapacity() {
        assertEquals(50, seaportTerminal.getShipCapacity());
    }

    @Test
    void seaportTerminalShouldHaveCorrectOperatingCosts() {
        assertEquals(MoneyAmount.of(12000.0f), seaportTerminal.getOperatingCosts());
    }
}