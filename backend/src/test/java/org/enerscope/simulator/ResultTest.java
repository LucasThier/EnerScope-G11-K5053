package org.enerscope.simulator;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.simulator.simNode.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class ResultTest {
    private void setupBaseNodeMocks(BaseNode mockNode, UUID id) {
        when(mockNode.getId()).thenReturn(id);
        when(mockNode.getMaintenanceIntervalInDays()).thenReturn(500);
        when(mockNode.getMaintenanceDuration()).thenReturn(24);
        when(mockNode.getLifespanInMonths()).thenReturn(300);
    }

    @Test
    public void testResultWell(){
        UUID wellId = UUID.randomUUID();
        UUID gatheringId = UUID.randomUUID();

        Well mockWell = Mockito.mock(Well.class);
        setupBaseNodeMocks(mockWell, wellId);
        when(mockWell.getMaxCollectionCapacity()).thenReturn(1000f);
        when(mockWell.getDeclineCurve()).thenReturn(2f);
        when(mockWell.getGasRichness()).thenReturn(1f);
        when(mockWell.getDTMTime()).thenReturn(24);

        GatheringNetwork mockGathering = Mockito.mock(GatheringNetwork.class);
        setupBaseNodeMocks(mockGathering, gatheringId);
        when(mockGathering.getMaxTransportCapacity()).thenReturn(500f);
        when(mockGathering.getLength()).thenReturn(5f);
        when(mockGathering.getLossPerMeter()).thenReturn(0.01f);

        SimWell simWell = new SimWell(mockWell);
        SimGatheringNetwork simGatheringNetwork = new SimGatheringNetwork(mockGathering);

        simGatheringNetwork.addPreviousNode(simWell);

        for (int t = 0; t < 8760; t++) {
            simWell.simulate(t);
            simGatheringNetwork.simulate(t);
            simGatheringNetwork.deliver(simGatheringNetwork.getToDeliver().getAmount());
        }

        ResultPerNode wellResult = simWell.createResult();

//        System.out.println("=== RESULTADO POZO ===");
//        System.out.println("Node ID: " + wellResult.getNodeID());
//        System.out.println("Max Possible Produced: " + wellResult.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + wellResult.getTotalProduced());
//        System.out.println("Total Deferred: " + wellResult.getTotalDeferred());
//        System.out.println("Total Extra: " + wellResult.getExtra());

        assertNotNull(wellResult.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(8760000f, wellResult.getTotalProduced(),0.1f);
        assertEquals(8760000f,wellResult.getMaxPossibleProduced(),0.1f);
        assertEquals(4380000f,wellResult.getTotalDeferred(),0.1f);
        assertEquals(0f,wellResult.getExtra(),0.1f);
    }

    @Test
    public void testResultGatheringNetwork(){
        UUID wellId = UUID.randomUUID();
        UUID gatheringId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        Well mockWell = Mockito.mock(Well.class);
        setupBaseNodeMocks(mockWell, wellId);
        when(mockWell.getMaxCollectionCapacity()).thenReturn(1000f);
        when(mockWell.getDeclineCurve()).thenReturn(2f);
        when(mockWell.getGasRichness()).thenReturn(1f);
        when(mockWell.getDTMTime()).thenReturn(24);

        GatheringNetwork mockGathering = Mockito.mock(GatheringNetwork.class);
        setupBaseNodeMocks(mockGathering, gatheringId);
        when(mockGathering.getMaxTransportCapacity()).thenReturn(1200f);
        when(mockGathering.getLength()).thenReturn(5f);
        when(mockGathering.getLossPerMeter()).thenReturn(1f);

        TreatmentPlant mockTreatment = Mockito.mock(TreatmentPlant.class);
        setupBaseNodeMocks(mockTreatment, treatmentId);
        when(mockTreatment.getMaxTreatmentCapacity()).thenReturn(800f);
        when(mockTreatment.getIntermediateStorage()).thenReturn(0f);

        SimWell simWell =new SimWell(mockWell);
        SimGatheringNetwork simGatheringNetwork = new SimGatheringNetwork(mockGathering);
        SimTreatmentPlant simTreatmentPlant = new SimTreatmentPlant(mockTreatment);

        simGatheringNetwork.addPreviousNode(simWell);
        simTreatmentPlant.addPreviousNode(simGatheringNetwork);

        for (int t = 0; t < 8760; t++) {
            simWell.simulate(t);
            simGatheringNetwork.simulate(t);
            simTreatmentPlant.simulate(t);
            simTreatmentPlant.deliver(simTreatmentPlant.getToDeliver().getAmount());
        }

        ResultPerNode gatheringNetworkResult = simGatheringNetwork.createResult();

//        System.out.println("=== RESULTADO RED ===");
//        System.out.println("Node ID: " + gatheringNetworkResult.getNodeID());
//        System.out.println("Max Possible Produced: " + gatheringNetworkResult.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + gatheringNetworkResult.getTotalProduced());
//        System.out.println("Total Deferred: " + gatheringNetworkResult.getTotalDeferred());
//        System.out.println("Total Extra: " + gatheringNetworkResult.getExtra());

        assertNotNull(gatheringNetworkResult.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(10512000f,gatheringNetworkResult.getMaxPossibleProduced(),0.1f);
        // New material processed: two startup hours, one transition, then steady throughput.
        assertEquals((float)(950 * 2 + 862.125 + 817 * 8757), gatheringNetworkResult.getTotalProduced(),0.5f);
        assertEquals(340f,gatheringNetworkResult.getTotalDeferred(),0.1f);
        assertEquals(525587.5f,gatheringNetworkResult.getExtra(),0.1f);
    }

    @Test
    public void testResultTreatmentPlant(){
        UUID gatheringId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();

        GatheringNetwork mockGathering = Mockito.mock(GatheringNetwork.class);
        setupBaseNodeMocks(mockGathering, gatheringId);
        when(mockGathering.getMaxTransportCapacity()).thenReturn(1000f);
        when(mockGathering.getLength()).thenReturn(5f);
        when(mockGathering.getLossPerMeter()).thenReturn(0.01f);

        TreatmentPlant mockTreatment = Mockito.mock(TreatmentPlant.class);
        setupBaseNodeMocks(mockTreatment, treatmentId);
        when(mockTreatment.getMaxTreatmentCapacity()).thenReturn(1000f);
        when(mockTreatment.getIntermediateStorage()).thenReturn(500f);

        Pipeline mockPipeline = Mockito.mock(Pipeline.class);
        setupBaseNodeMocks(mockPipeline, pipelineId);
        when(mockPipeline.getMaxFlowCapacity()).thenReturn(800f);
        when(mockPipeline.getLength()).thenReturn(12f);
        when(mockPipeline.getLossPerKm()).thenReturn(0.05f);

        SimGatheringNetwork simGatheringNetwork = new SimGatheringNetwork(mockGathering);
        SimTreatmentPlant simTreatmentPlant = new SimTreatmentPlant(mockTreatment);
        SimPipeline simPipeline = new SimPipeline(mockPipeline);

        simTreatmentPlant.addPreviousNode(simGatheringNetwork);
        simPipeline.addPreviousNode(simTreatmentPlant);

        for (int t = 0; t < 8760; t++) {
            simGatheringNetwork.setToDeliver(new ToDeliver(1000f,10f));
            simTreatmentPlant.simulate(t);
            simPipeline.simulate(t);
            simPipeline.deliver(simPipeline.getToDeliver().getAmount());
        }

        ResultPerNode resultTreatmentPlant = simTreatmentPlant.createResult();

//        System.out.println("=== RESULTADO PLANTA TRATAMIENTO ===");
//        System.out.println("Node ID: " + resultTreatmentPlant.getNodeID());
//        System.out.println("Max Possible Produced: " + resultTreatmentPlant.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + resultTreatmentPlant.getTotalProduced());
//        System.out.println("Total Deferred: " + resultTreatmentPlant.getTotalDeferred());
//        System.out.println("Total Extra: " + resultTreatmentPlant.getExtra());

        assertNotNull(resultTreatmentPlant.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(8760000f,resultTreatmentPlant.getMaxPossibleProduced(),0.1f);
        assertEquals(7008152f, resultTreatmentPlant.getTotalProduced(),0.1f);
        assertEquals(152.06732f,resultTreatmentPlant.getTotalDeferred(),0.1f);
        assertEquals(420102.8f,resultTreatmentPlant.getExtra(),0.1f);
    }

    @Test
    public void testResultPipeline(){
        UUID treatmentId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID compressionId = UUID.randomUUID();

        TreatmentPlant mockTreatment = Mockito.mock(TreatmentPlant.class);
        setupBaseNodeMocks(mockTreatment, treatmentId);
        when(mockTreatment.getMaxTreatmentCapacity()).thenReturn(1000f);
        when(mockTreatment.getIntermediateStorage()).thenReturn(500f);

        Pipeline mockPipeline = Mockito.mock(Pipeline.class);
        setupBaseNodeMocks(mockPipeline, pipelineId);
        when(mockPipeline.getMaxFlowCapacity()).thenReturn(1000f);
        when(mockPipeline.getLength()).thenReturn(100f);
        when(mockPipeline.getLossPerKm()).thenReturn(0.1f);

        CompressingPlant mockCompression = Mockito.mock(CompressingPlant.class);
        setupBaseNodeMocks(mockCompression, compressionId);
        when(mockCompression.getMaxCompressionCapacity()).thenReturn(800f);
        when(mockCompression.getProcessWaste()).thenReturn(1f);
        when(mockCompression.getGasConsumption()).thenReturn(2f);

        SimTreatmentPlant simTreatmentPlant = new SimTreatmentPlant(mockTreatment);
        SimPipeline simPipeline = new SimPipeline(mockPipeline);
        SimCompressingPlant simCompressingPlant = new SimCompressingPlant(mockCompression);

        simPipeline.addPreviousNode(simTreatmentPlant);
        simCompressingPlant.addPreviousNode(simPipeline);

        for (int t = 0; t < 8760; t++) {
            simTreatmentPlant.setToDeliver(new ToDeliver(1000f,0f));
            simPipeline.simulate(t);
            simCompressingPlant.simulate(t);
            simCompressingPlant.deliver(simCompressingPlant.getToDeliver().getAmount());
        }

        ResultPerNode resultPipeline = simPipeline.createResult();

//        System.out.println("=== RESULTADO PIPELINE ===");
//        System.out.println("Node ID: " + resultPipeline.getNodeID());
//        System.out.println("Max Possible Produced: " + resultPipeline.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + resultPipeline.getTotalProduced());
//        System.out.println("Total Deferred: " + resultPipeline.getTotalDeferred());
//        System.out.println("Total Extra: " + resultPipeline.getExtra());

        assertNotNull(resultPipeline.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(8760000f,resultPipeline.getMaxPossibleProduced(),0.1f);
        assertEquals(900f + 810f * 8759, resultPipeline.getTotalProduced(),0.1f);
        assertEquals(100f,resultPipeline.getTotalDeferred(),0.1f);
        assertEquals(876000f,resultPipeline.getExtra(),0.1f);
    }

    @Test
    public void testResultCompressingPlant(){
        UUID pipelineId = UUID.randomUUID();
        UUID compressionId = UUID.randomUUID();
        UUID LiquefactionId = UUID.randomUUID();

        Pipeline mockPipeline = Mockito.mock(Pipeline.class);
        setupBaseNodeMocks(mockPipeline, pipelineId);
        when(mockPipeline.getMaxFlowCapacity()).thenReturn(1000f);
        when(mockPipeline.getLength()).thenReturn(100f);
        when(mockPipeline.getLossPerKm()).thenReturn(0.1f);

        CompressingPlant mockCompression = Mockito.mock(CompressingPlant.class);
        setupBaseNodeMocks(mockCompression, compressionId);
        when(mockCompression.getMaxCompressionCapacity()).thenReturn(1000f);
        when(mockCompression.getProcessWaste()).thenReturn(3f);
        when(mockCompression.getGasConsumption()).thenReturn(2f);

        GroundBasedLiquefactionPlant mockGroundLiquefaction = Mockito.mock(GroundBasedLiquefactionPlant.class);
        setupBaseNodeMocks(mockGroundLiquefaction, LiquefactionId);
        when(mockGroundLiquefaction.getMaxProcessingCapacity()).thenReturn(900f);
        when(mockGroundLiquefaction.getMTPARatio()).thenReturn(80f);
        when(mockGroundLiquefaction.getIntermediateStorage()).thenReturn(0f);
        when(mockGroundLiquefaction.getGasConsumption()).thenReturn(1.5f);

        SimPipeline simPipeline = new SimPipeline(mockPipeline);
        SimCompressingPlant simCompressingPlant = new SimCompressingPlant(mockCompression);
        SimLiquefactionPlant simLiquefactionPlant = new SimLiquefactionPlant(mockGroundLiquefaction);

        simCompressingPlant.addPreviousNode(simPipeline);
        simLiquefactionPlant.addPreviousNode(simCompressingPlant);

        for (int t = 0; t < 8760; t++) {
            simPipeline.setToDeliver(new ToDeliver(1000f,0f));
            simCompressingPlant.simulate(t);
            simLiquefactionPlant.simulate(t);
            simLiquefactionPlant.deliver(simLiquefactionPlant.getToDeliver().getAmount());
        }

        ResultPerNode resultCompressingPlant = simCompressingPlant.createResult();

//        System.out.println("=== RESULTADO CompressingPlant ===");
//        System.out.println("Node ID: " + resultCompressingPlant.getNodeID());
//        System.out.println("Max Possible Produced: " + resultCompressingPlant.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + resultCompressingPlant.getTotalProduced());
//        System.out.println("Total Deferred: " + resultCompressingPlant.getTotalDeferred());
//        System.out.println("Total Extra: " + resultCompressingPlant.getExtra());

        assertNotNull(resultCompressingPlant.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(8760000f,resultCompressingPlant.getMaxPossibleProduced(),0.1f);
        assertEquals(1000f * 0.95f * 8760, resultCompressingPlant.getTotalProduced(),0.1f);
        assertEquals(50f,resultCompressingPlant.getTotalDeferred(),0.1f);
        assertEquals(438000f,resultCompressingPlant.getExtra(),0.1f);
    }

    @Test
    public void testResultLiquefactionPlant(){
        UUID compressionId = UUID.randomUUID();
        UUID LiquefactionId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();

        CompressingPlant mockCompression = Mockito.mock(CompressingPlant.class);
        setupBaseNodeMocks(mockCompression, compressionId);
        when(mockCompression.getMaxCompressionCapacity()).thenReturn(1000f);
        when(mockCompression.getProcessWaste()).thenReturn(3f);
        when(mockCompression.getGasConsumption()).thenReturn(2f);

        GroundBasedLiquefactionPlant mockGroundLiquefaction = Mockito.mock(GroundBasedLiquefactionPlant.class);
        setupBaseNodeMocks(mockGroundLiquefaction, LiquefactionId);
        when(mockGroundLiquefaction.getMaxProcessingCapacity()).thenReturn(1000f);
        when(mockGroundLiquefaction.getMTPARatio()).thenReturn(90f);
        when(mockGroundLiquefaction.getIntermediateStorage()).thenReturn(3000f);
        when(mockGroundLiquefaction.getGasConsumption()).thenReturn(5f);

        SeaportTerminal mockTerminal = Mockito.mock(SeaportTerminal.class);
        setupBaseNodeMocks(mockTerminal, terminalId);
        when(mockTerminal.getIntermediateStorage()).thenReturn(600f);
        when(mockTerminal.getShipCapacity()).thenReturn(2);

        SimCompressingPlant simCompressingPlant = new SimCompressingPlant(mockCompression);
        SimLiquefactionPlant simLiquefactionPlant = new SimLiquefactionPlant(mockGroundLiquefaction);
        SimSeaportTerminal simSeaportTerminal = new SimSeaportTerminal(mockTerminal);

        simLiquefactionPlant.addPreviousNode(simCompressingPlant);
        simSeaportTerminal.addPreviousNode(simLiquefactionPlant);

        for (int t = 0; t < 8760; t++) {
            simCompressingPlant.setToDeliver(new ToDeliver(1000f,5f));
            simLiquefactionPlant.simulate(t);
            simSeaportTerminal.simulate(t);
            simSeaportTerminal.deliver(simSeaportTerminal.getToDeliver().getAmount());
        }

        ResultPerNode resultLiquefactionPlant = simLiquefactionPlant.createResult();

//        System.out.println("=== RESULTADO LiquefactionPlant ===");
//        System.out.println("Node ID: " + resultLiquefactionPlant.getNodeID());
//        System.out.println("Max Possible Produced: " + resultLiquefactionPlant.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + resultLiquefactionPlant.getTotalProduced());
//        System.out.println("Total Deferred: " + resultLiquefactionPlant.getTotalDeferred());
//        System.out.println("Total Extra: " + resultLiquefactionPlant.getExtra());

        assertNotNull(resultLiquefactionPlant.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(8760000f,resultLiquefactionPlant.getMaxPossibleProduced(),0.1f);
        assertEquals((float)(1000 * 0.95 * 0.95 * 0.90 * 8760), resultLiquefactionPlant.getTotalProduced(),0.5f);
        assertEquals(2400f,resultLiquefactionPlant.getTotalDeferred(),0.1f);
        assertEquals(854100f,resultLiquefactionPlant.getExtra(),0.1f);
    }

    @Test
    public void testResultSeaportTerminal(){
        UUID LiquefactionId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();

        GroundBasedLiquefactionPlant mockGroundLiquefaction = Mockito.mock(GroundBasedLiquefactionPlant.class);
        setupBaseNodeMocks(mockGroundLiquefaction, LiquefactionId);
        when(mockGroundLiquefaction.getMaxProcessingCapacity()).thenReturn(1000f);
        when(mockGroundLiquefaction.getMTPARatio()).thenReturn(90f);
        when(mockGroundLiquefaction.getIntermediateStorage()).thenReturn(3000f);
        when(mockGroundLiquefaction.getGasConsumption()).thenReturn(5f);

        SeaportTerminal mockTerminal = Mockito.mock(SeaportTerminal.class);
        setupBaseNodeMocks(mockTerminal, terminalId);
        when(mockTerminal.getIntermediateStorage()).thenReturn(2000f);
        when(mockTerminal.getShipCapacity()).thenReturn(2);

        LNGCarrier mockCarrier = Mockito.mock(LNGCarrier.class);
        setupBaseNodeMocks(mockCarrier, carrierId);
        when(mockCarrier.getExportFrequency()).thenReturn(7);
        when(mockCarrier.getShipCapacity()).thenReturn(500f);
        when(mockCarrier.getFullLoadTime()).thenReturn(24f);
        when(mockCarrier.getTimeToDestination()).thenReturn(72);

        SimLiquefactionPlant simLiquefactionPlant = new SimLiquefactionPlant(mockGroundLiquefaction);
        SimSeaportTerminal simSeaportTerminal = new SimSeaportTerminal(mockTerminal);
        SimLNGCarrier simLNGCarrier = new SimLNGCarrier(mockCarrier);

        simSeaportTerminal.addPreviousNode(simLiquefactionPlant);
        simLNGCarrier.addPreviousNode(simSeaportTerminal);

        for (int t = 0; t < 8760; t++) {
            simLiquefactionPlant.setAmountInIntermediateStorage(new ToDeliver(750f,0f));
            simSeaportTerminal.simulate(t);
            simLNGCarrier.simulate(t);
        }

        ResultPerNode resultSeaportTerminal = simSeaportTerminal.createResult();

//        System.out.println("=== RESULTADO SeaportTerminal ===");
//        System.out.println("Node ID: " + resultSeaportTerminal.getNodeID());
//        System.out.println("Max Possible Produced: " + resultSeaportTerminal.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + resultSeaportTerminal.getTotalProduced());
//        System.out.println("Total Deferred: " + resultSeaportTerminal.getTotalDeferred());
//        System.out.println("Total Extra: " + resultSeaportTerminal.getExtra());

        assertNotNull(resultSeaportTerminal.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(17520000f,resultSeaportTerminal.getMaxPossibleProduced(),0.1f);
        // 42 complete cargoes of 1,000 units plus 2,000 units in terminal storage.
        assertEquals(44000f, resultSeaportTerminal.getTotalProduced(),0.1f);
        assertEquals(2000f,resultSeaportTerminal.getTotalDeferred(),0.1f);
        assertEquals(0f,resultSeaportTerminal.getExtra(),0.1f);
    }

    @Test
    public void testResultLNGCarrier(){
        UUID terminalId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();

        SeaportTerminal mockTerminal = Mockito.mock(SeaportTerminal.class);
        setupBaseNodeMocks(mockTerminal, terminalId);
        when(mockTerminal.getIntermediateStorage()).thenReturn(2000f);
        when(mockTerminal.getShipCapacity()).thenReturn(2);

        LNGCarrier mockCarrier = Mockito.mock(LNGCarrier.class);
        setupBaseNodeMocks(mockCarrier, carrierId);
        when(mockCarrier.getExportFrequency()).thenReturn(7);
        when(mockCarrier.getShipCapacity()).thenReturn(1000f);
        when(mockCarrier.getFullLoadTime()).thenReturn(24f);
        when(mockCarrier.getTimeToDestination()).thenReturn(72);

        SimSeaportTerminal simSeaportTerminal = new SimSeaportTerminal(mockTerminal);
        SimLNGCarrier simLNGCarrier = new SimLNGCarrier(mockCarrier);

        simLNGCarrier.addPreviousNode(simSeaportTerminal);

        for (int t = 0; t < 8760; t++) {
            simSeaportTerminal.setAmountInIntermediateStorage(new ToDeliver(40f,0f));
            simLNGCarrier.simulate(t);
        }

        ResultPerNode resultLNGCarrier = simLNGCarrier.createResult();

//        System.out.println("=== RESULTADO LNGCarrier ===");
//        System.out.println("Node ID: " + resultLNGCarrier.getNodeID());
//        System.out.println("Max Possible Produced: " + resultLNGCarrier.getMaxPossibleProduced());
//        System.out.println("Total Produced: " + resultLNGCarrier.getTotalProduced());
//        System.out.println("Total Deferred: " + resultLNGCarrier.getTotalDeferred());
//        System.out.println("Total Extra: " + resultLNGCarrier.getExtra());

        assertNotNull(resultLNGCarrier.getNodeID(), "El Pozo debe tener un ID asignado");

        assertEquals(86457.84f,resultLNGCarrier.getMaxPossibleProduced(),0.1f);
        assertEquals(83000.0f, resultLNGCarrier.getTotalProduced(),0.1f);
        assertEquals(0f,resultLNGCarrier.getTotalDeferred(),0.1f);
        assertEquals(0f,resultLNGCarrier.getExtra(),0.1f);
    }
}
