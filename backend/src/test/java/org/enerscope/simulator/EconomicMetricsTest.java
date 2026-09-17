package org.enerscope.simulator;

import java.util.*;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.node.model.*;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.transportation.*;
import org.enerscope.node.model.export.*;
import org.enerscope.simulator.simNode.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomicMetricsTest {
    @Test void treatmentCountsSmallerNewBatchWithoutSubtractingPreviousBatch() {
        var source=new SimGatheringNetwork(node(org.enerscope.node.model.extraction.GatheringNetwork.class));
        var physical=node(org.enerscope.node.model.extraction.TreatmentPlant.class);
        when(physical.getMaxTreatmentCapacity()).thenReturn(200f);
        var treatment=new SimTreatmentPlant(physical);treatment.addPreviousNode(source);
        source.setToDeliver(new ToDeliver(100,0));treatment.simulate(0);
        source.setToDeliver(new ToDeliver(50,0));treatment.simulate(1);
        assertEquals(150,treatment.getMeasuredOutput());assertEquals(150,treatment.getMeasuredInput());
    }
    private <T extends BaseNode> T node(Class<T> type) {
        T n=mock(type);when(n.getId()).thenReturn(UUID.randomUUID());when(n.getLifespanInMonths()).thenReturn(120);return n;
    }
    @Test void compressorCountsNewOutputEvenWhenPreviousOutputRemains() {
        var source=new SimWell(node(Well.class));var physical=node(CompressingPlant.class);
        when(physical.getMaxCompressionCapacity()).thenReturn(100f);
        var compressor=new SimCompressingPlant(physical);compressor.addPreviousNode(source);
        source.setToDeliver(new ToDeliver(100,0));compressor.simulate(0);
        source.setToDeliver(new ToDeliver(100,0));compressor.simulate(1);
        assertEquals(200,compressor.getMeasuredInput());assertEquals(200,compressor.getMeasuredOutput());
        assertEquals(100,compressor.getTotalDeferred());
    }
    @Test void pipelineConnectionTransfersActualQuantityAndHonorsPredecessorOrder() {
        var source=new SimWell(node(Well.class));var physical=node(PipelineConnection.class);
        when(physical.getTransferCapacity()).thenReturn(40f);var connection=new SimPipelineConnection(physical);connection.addPreviousNode(source);
        assertFalse(connection.readyToBeProcessed(0));source.setLastSimulatedTime(0);assertTrue(connection.readyToBeProcessed(0));
        source.setToDeliver(new ToDeliver(60,0));connection.simulate(0);
        assertEquals(40,connection.getMeasuredOutput());assertEquals(20,source.getToDeliver().getAmount());
    }
    @Test void shipmentCountsDepartureOnceAndDoesNotSellPartialLoading() {
        var terminalPhysical=node(SeaportTerminal.class);when(terminalPhysical.getShipCapacity()).thenReturn(1);
        var terminal=new SimSeaportTerminal(terminalPhysical);terminal.setAmountInIntermediateStorage(new ToDeliver(1000,0));
        var carrierPhysical=node(LNGCarrier.class);when(carrierPhysical.getShipCapacity()).thenReturn(100f);
        when(carrierPhysical.getFullLoadTime()).thenReturn(3f);when(carrierPhysical.getTimeToDestination()).thenReturn(100);
        var ship=new SimLNGCarrier(carrierPhysical);ship.addPreviousNode(terminal);
        ship.simulate(0);ship.simulate(1);
        assertEquals(0,ship.getMeasuredExported());assertEquals(0,ship.getMeasuredEvents());
        for(int t=2;t<10;t++)ship.simulate(t);
        assertEquals(100,ship.getMeasuredExported(),.001);assertEquals(1,ship.getMeasuredEvents());
        assertEquals(100,ship.getMeasuredInput(),.001);assertEquals(900,terminal.getToDeliver().getAmount(),.001);
    }
    @Test void simulatorRejectsRepeatedExecutionInsteadOfAccumulatingResults() {
        var simulator=new Simulator(EconomicExample.version());simulator.simulate(1);
        assertThrows(IllegalStateException.class,()->simulator.simulate(1));
        assertEquals(8760,simulator.getAnnualMetrics().getFirst().operatingHours().intValueExact());
    }
    @Test void simulatorRejectsInvalidHorizons() {
        assertThrows(IllegalArgumentException.class,()->new Simulator(EconomicExample.version()).simulate(0));
        assertThrows(IllegalArgumentException.class,()->new Simulator(EconomicExample.version()).simulate(101));
    }
}
