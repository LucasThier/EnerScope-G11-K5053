package org.enerscope.simulator;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class ResultTest {
    @Test
    public void testResultInFiveYearSimulationWithAllNodeTypes() {
        // 1. Crear UUIDs para los 9 tipos de nodo
        UUID wellId = UUID.randomUUID();
        UUID gatheringId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID compressionId = UUID.randomUUID();
        UUID groundLiquefactionId = UUID.randomUUID();
        UUID flngLiquefactionId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();

        // 2. Instanciar y configurar Mocks de BaseNode
        Well mockWell = Mockito.mock(Well.class);
        setupBaseNodeMocks(mockWell, wellId);
        when(mockWell.getMaxCollectionCapacity()).thenReturn(100f);
        when(mockWell.getDeclineCurve()).thenReturn(2f);
        when(mockWell.getGasRichness()).thenReturn(1f);
        when(mockWell.getDTMTime()).thenReturn(24);

        GatheringNetwork mockGathering = Mockito.mock(GatheringNetwork.class);
        setupBaseNodeMocks(mockGathering, gatheringId);
        when(mockGathering.getMaxTransportCapacity()).thenReturn(150f);
        when(mockGathering.getLength()).thenReturn(5f);
        when(mockGathering.getLossPerMeter()).thenReturn(0.01f);

        TreatmentPlant mockTreatment = Mockito.mock(TreatmentPlant.class);
        setupBaseNodeMocks(mockTreatment, treatmentId);
        when(mockTreatment.getMaxTreatmentCapacity()).thenReturn(120f);
        when(mockTreatment.getIntermediateStorage()).thenReturn(500f);

        Pipeline mockPipeline = Mockito.mock(Pipeline.class);
        setupBaseNodeMocks(mockPipeline, pipelineId);
        when(mockPipeline.getMaxFlowCapacity()).thenReturn(110f);
        when(mockPipeline.getLength()).thenReturn(12f);
        when(mockPipeline.getLossPerKm()).thenReturn(0.05f);

        CompressingPlant mockCompression = Mockito.mock(CompressingPlant.class);
        setupBaseNodeMocks(mockCompression, compressionId);
        when(mockCompression.getMaxCompressionCapacity()).thenReturn(100f);
        when(mockCompression.getProcessWaste()).thenReturn(1f);
        when(mockCompression.getGasConsumption()).thenReturn(2f);

        GroundBasedLiquefactionPlant mockGroundLiquefaction = Mockito.mock(GroundBasedLiquefactionPlant.class);
        setupBaseNodeMocks(mockGroundLiquefaction, groundLiquefactionId);
        when(mockGroundLiquefaction.getMaxProcessingCapacity()).thenReturn(90f);
        when(mockGroundLiquefaction.getMTPARatio()).thenReturn(80f);
        when(mockGroundLiquefaction.getIntermediateStorage()).thenReturn(300f);
        when(mockGroundLiquefaction.getGasConsumption()).thenReturn(1.5f);

        FLNGUnit mockFLNGLiquefaction = Mockito.mock(FLNGUnit.class);
        setupBaseNodeMocks(mockFLNGLiquefaction, flngLiquefactionId);
        when(mockFLNGLiquefaction.getMaxProcessingCapacity()).thenReturn(80f);
        when(mockFLNGLiquefaction.getMTPARatio()).thenReturn(85f);
        when(mockFLNGLiquefaction.getIntermediateStorage()).thenReturn(250f);
        when(mockFLNGLiquefaction.getGasConsumption()).thenReturn(1.2f);

        SeaportTerminal mockTerminal = Mockito.mock(SeaportTerminal.class);
        setupBaseNodeMocks(mockTerminal, terminalId);
        when(mockTerminal.getIntermediateStorage()).thenReturn(1000f);
        when(mockTerminal.getShipCapacity()).thenReturn(2);

        LNGCarrier mockCarrier = Mockito.mock(LNGCarrier.class);
        setupBaseNodeMocks(mockCarrier, carrierId);
        when(mockCarrier.getExportFrequency()).thenReturn(7);
        when(mockCarrier.getShipCapacity()).thenReturn(500f);
        when(mockCarrier.getFullLoadTime()).thenReturn(24f);
        when(mockCarrier.getTimeToDestination()).thenReturn(72);

        List<BaseNode> nodes = Arrays.asList(
                mockWell, mockGathering, mockTreatment, mockPipeline,
                mockCompression, mockGroundLiquefaction, mockFLNGLiquefaction,
                mockTerminal, mockCarrier
        );

        // 3. Crear el encadenamiento de la red
        List<NodeConnection> connections = Arrays.asList(
                createConnection(wellId, gatheringId),
                createConnection(gatheringId, treatmentId),
                createConnection(treatmentId, pipelineId),
                createConnection(pipelineId, compressionId),
                createConnection(compressionId, groundLiquefactionId),
                createConnection(compressionId, flngLiquefactionId),
                createConnection(groundLiquefactionId, terminalId),
                createConnection(flngLiquefactionId, terminalId),
                createConnection(terminalId, carrierId)
        );

        // 4. Inicializar Simulador y ejecutar 5 años
        Simulator simulator = new Simulator(nodes, connections);
        simulator.simulate(5);

        // 5. Validar Resultados
        Result result = simulator.getResult();

        System.out.println("=== RESULTADO GENERAL ===");
        System.out.println("Result: " + result);
        System.out.println("Año de simulación: " + result.getYear());
        System.out.println("Total de nodos procesados: " + result.getResultPerNodes().size());

        System.out.println("\n=== DETALLE POR NODO ===");
        List<ResultPerNode> nodeResults = result.getResultPerNodes();

        nodeResults.forEach(nodeResult -> {
            System.out.println("----------------------------------------");
            System.out.println("Node ID: " + nodeResult.getNodeID());
            System.out.println("Node Class: " + nodeResult.getNodeClass());
            System.out.println("Max Possible Produced: " + nodeResult.getMaxPossibleProduced());
            System.out.println("Total Produced: " + nodeResult.getTotalProduced());
            System.out.println("Total Deferred: " + nodeResult.getTotalDeferred());
            System.out.println("Total extra: " + nodeResult.getExtra());
        });


        assertNotNull(result, "El resultado general no debe ser nulo");
        assertEquals(5, result.getYear(), "El año configurado debe coincidir");

        assertEquals(9, nodeResults.size(), "Debe haber un resultado registrado por cada uno de los 9 nodos");

        // 6. Asertar metricas individuales por cada nodo
        nodeResults.forEach(nodeResult -> {
            assertNotNull(nodeResult.getNodeID(), "Cada resultado debe tener un ID asignado");
            assertTrue(nodeResult.getMaxPossibleProduced() >= 0, "La producción máxima posible debe ser válida");
            assertTrue(nodeResult.getTotalProduced() >= 0, "La producción total no puede ser negativa");
            assertTrue(nodeResult.getTotalDeferred() >= 0, "El diferido total no puede ser negativo");
        });
    }

    private void setupBaseNodeMocks(BaseNode mockNode, UUID id) {
        when(mockNode.getId()).thenReturn(id);
        when(mockNode.getMaintenanceIntervalInDays()).thenReturn(9999);
        when(mockNode.getMaintenanceDuration()).thenReturn(24);
        when(mockNode.getLifespanInMonths()).thenReturn(999);
    }

    private NodeConnection createConnection(UUID fromId, UUID toId) {
        NodeConnection connection = Mockito.mock(NodeConnection.class);
        when(connection.getFromNodeId()).thenReturn(fromId);
        when(connection.getToNodeId()).thenReturn(toId);
        return connection;
    }
}
