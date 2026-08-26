package org.enerscope.simulator;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.simulator.simNode.SimWell;
import org.enerscope.simulator.simNode.SimGatheringNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class SimulatorTest {

    private Well mockWell;
    private GatheringNetwork mockGatheringNetwork;

    @BeforeEach
    void setUp() {
        mockWell = Mockito.mock(Well.class);
        when(mockWell.getId()).thenReturn(UUID.randomUUID());
        when(mockWell.getMaintenanceIntervalInDays()).thenReturn(30);
        when(mockWell.getMaintenanceDuration()).thenReturn(24);
        when(mockWell.getLifespanInMonths()).thenReturn(120);
        when(mockWell.getMaxCollectionCapacity()).thenReturn(1000f);
        when(mockWell.getDeclineCurve()).thenReturn(5f);

        mockGatheringNetwork = Mockito.mock(GatheringNetwork.class);
        when(mockGatheringNetwork.getId()).thenReturn(UUID.randomUUID());
        when(mockGatheringNetwork.getMaxTransportCapacity()).thenReturn(5000f);
        when(mockGatheringNetwork.getLength()).thenReturn(10f);
        when(mockGatheringNetwork.getLossPerMeter()).thenReturn(0.01f);
    }

    @Test
    public void testSimWellActiveAction() {
        SimWell simWell = new SimWell(mockWell);

        simWell.simulate(0);
        assertEquals(1000f, simWell.getToDeliver(), "El pozo debería entregar su capacidad máxima en la hora 0");

        simWell.simulate(1);

        assertEquals(950f, simWell.getToDeliver(), "El pozo debería haber aplicado la curva de declive tras 1 año");
    }

    @Test
    public void testSimGatheringNetworkCapacity() {
        SimGatheringNetwork network = new SimGatheringNetwork(mockGatheringNetwork);

        SimWell simWell = new SimWell(mockWell);
        simWell.simulate(0);

        network.addPreviousNode(simWell);

        network.simulate(0);

        assertEquals(1000f, network.getToDeliver());
    }

    @Test
    @Timeout(value = 5)
    public void testSimulatorExecutionTime() {
        List<BaseNode> baseNodes = new ArrayList<>();
        baseNodes.add((BaseNode) mockWell);
        baseNodes.add((BaseNode) mockGatheringNetwork);

        List<NodeConnection> connections = new ArrayList<>();

        Simulator simulator = new Simulator(baseNodes, connections);

        long startTime = System.currentTimeMillis();

        simulator.simulate(1);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Tiempo de simulación para 1 año(s): " + duration + " ms");

        assertTrue(duration < 1000, "La simulación tardó demasiado: " + duration + " ms");
    }
}
