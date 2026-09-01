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
import org.enerscope.simulator.simNode.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class SimulatorTest {

    private Well mockWell;
    private GatheringNetwork mockGatheringNetwork;
    private TreatmentPlant mockTreatmentPlant;
    private Pipeline mockPipeline;
    private CompressingPlant mockCompressingPlant;
    private GroundBasedLiquefactionPlant mockLiquefactionPlant;
    private SeaportTerminal mockSeaportTerminal;
    private LNGCarrier mockCarrier;

    private UUID idWell, idGn, idTp, idPipe, idCp, idLp, idSt, idCarrier;

    @BeforeEach
    void setUp() {
        idWell = UUID.randomUUID();
        idGn = UUID.randomUUID();
        idTp = UUID.randomUUID();
        idPipe = UUID.randomUUID();
        idCp = UUID.randomUUID();
        idLp = UUID.randomUUID();
        idSt = UUID.randomUUID();
        idCarrier = UUID.randomUUID();

        mockWell = Mockito.mock(Well.class);
        mockGatheringNetwork = Mockito.mock(GatheringNetwork.class);
        mockTreatmentPlant = Mockito.mock(TreatmentPlant.class);
        mockPipeline = Mockito.mock(Pipeline.class);
        mockCompressingPlant = Mockito.mock(CompressingPlant.class);
        mockLiquefactionPlant = Mockito.mock(GroundBasedLiquefactionPlant.class);
        mockSeaportTerminal = Mockito.mock(SeaportTerminal.class);
        mockCarrier = Mockito.mock(LNGCarrier.class);

        configurarBaseNode(mockWell, idWell);
        configurarBaseNode(mockGatheringNetwork, idGn);
        configurarBaseNode(mockTreatmentPlant, idTp);
        configurarBaseNode(mockPipeline, idPipe);
        configurarBaseNode(mockCompressingPlant, idCp);
        configurarBaseNode(mockLiquefactionPlant, idLp);
        configurarBaseNode(mockSeaportTerminal, idSt);
        configurarBaseNode(mockCarrier, idCarrier);

        when(mockWell.getMaxCollectionCapacity()).thenReturn(1000f);
        when(mockWell.getDeclineCurve()).thenReturn(5f);
        when(mockWell.getGasRichness()).thenReturn(50f);

        when(mockGatheringNetwork.getMaxTransportCapacity()).thenReturn(5000f);
        when(mockGatheringNetwork.getLength()).thenReturn(10f);
        when(mockGatheringNetwork.getLossPerMeter()).thenReturn(0.01f);

        when(mockTreatmentPlant.getMaxTreatmentCapacity()).thenReturn(2000f);

        when(mockPipeline.getMaxFlowCapacity()).thenReturn(3000f);
        when(mockPipeline.getLength()).thenReturn(100f);
        when(mockPipeline.getLossPerKm()).thenReturn(0.05f);

        when(mockCompressingPlant.getMaxCompressionCapacity()).thenReturn(2500f);
        when(mockCompressingPlant.getProcessWaste()).thenReturn(2f);
        when(mockCompressingPlant.getGasConsumption()).thenReturn(3f);

        when(mockLiquefactionPlant.getMaxProcessingCapacity()).thenReturn(2000f);
        when(mockLiquefactionPlant.getGasConsumption()).thenReturn(10f);
        when(mockLiquefactionPlant.getMTPARatio()).thenReturn(50f);
        when(mockLiquefactionPlant.getIntermediateStorage()).thenReturn(10000f);

        when(mockSeaportTerminal.getIntermediateStorage()).thenReturn(50000f);
        when(mockSeaportTerminal.getShipCapacity()).thenReturn(2);

        when(mockCarrier.getShipCapacity()).thenReturn(1000f);
        when(mockCarrier.getFullLoadTime()).thenReturn(24f);
        when(mockCarrier.getExportFrequency()).thenReturn(10);
        when(mockCarrier.getTimeToDestination()).thenReturn(48);
    }

    private void configurarBaseNode(BaseNode mockNode, UUID id) {
        when(mockNode.getId()).thenReturn(id);
        when(mockNode.getMaintenanceIntervalInDays()).thenReturn(365); // Mantenimiento anual
        when(mockNode.getMaintenanceDuration()).thenReturn(24); // Tarda 24hs
        when(mockNode.getLifespanInMonths()).thenReturn(240); // 20 años de vida
    }

    private NodeConnection crearConexionMock(UUID from, UUID to) {
        NodeConnection connection = Mockito.mock(NodeConnection.class);
        when(connection.getFromNodeId()).thenReturn(from);
        when(connection.getToNodeId()).thenReturn(to);
        return connection;
    }

    @Test
    public void testSimWellActiveAction() {
        SimWell simWell = new SimWell(mockWell);

        simWell.simulate(0);
        assertEquals(1000f, simWell.getToDeliver().getAmount(), "Hora 0 debe ser 1000f");

        int unAnoEnHoras = 24 * 365;
        simWell.simulate(unAnoEnHoras);

        assertEquals(950f, simWell.getToDeliver().getAmount(), "El pozo debería haber aplicado la curva de declive tras 1 año");
    }

    @Test
    public void testSimGatheringNetworkCapacity() {
        SimGatheringNetwork network = new SimGatheringNetwork(mockGatheringNetwork);

        SimWell simWell = new SimWell(mockWell);
        simWell.simulate(0);

        network.addPreviousNode(simWell);

        network.simulate(0);

        assertEquals(1000f, network.getToDeliver().getAmount());
    }

    @Test
    public void testSimTreatmentPlant() {
        SimTreatmentPlant treatmentPlant = new SimTreatmentPlant(mockTreatmentPlant);
        SimGatheringNetwork network = new SimGatheringNetwork(mockGatheringNetwork);

        network.getToDeliver().mix(new ToDeliver(1000f, 5f));

        treatmentPlant.addPreviousNode(network);
        treatmentPlant.simulate(0);


        assertEquals(950f, treatmentPlant.getToDeliver().getAmount(), "El volumen de gas limpio no coincide tras remover el contaminante");
        assertEquals(0f, treatmentPlant.getToDeliver().getContaminant(), "El contaminante debería ser 0 tras pasar por el TreatmentPlant");
    }

    @Test
    public void testSimPipeline() {
        SimPipeline pipeline = new SimPipeline(mockPipeline);
        SimTreatmentPlant prevNode = new SimTreatmentPlant(mockTreatmentPlant);

        prevNode.deliver(-1000f);
        pipeline.addPreviousNode(prevNode);

        pipeline.simulate(0);

        assertEquals(950f, pipeline.getToDeliver().getAmount(), "El cálculo de flujo en el Pipeline falló");
    }

    @Test
    public void testSimCompressingPlant() {
        SimCompressingPlant compressingPlant = new SimCompressingPlant(mockCompressingPlant);
        SimPipeline prevNode = new SimPipeline(mockPipeline);

        prevNode.deliver(-1000f);

        compressingPlant.addPreviousNode(prevNode);

        compressingPlant.simulate(0);

        assertEquals(950f, compressingPlant.getToDeliver().getAmount(), "El cálculo de compresión falló");
    }

    @Test
    public void testSimLiquefactionPlant() {
        SimLiquefactionPlant liquefactionPlant = new SimLiquefactionPlant(mockLiquefactionPlant);
        SimCompressingPlant prevNode = new SimCompressingPlant(mockCompressingPlant);

        prevNode.deliver(-1000f);
        liquefactionPlant.addPreviousNode(prevNode);

        liquefactionPlant.simulate(0);

        assertEquals(450f, liquefactionPlant.getToDeliver().getAmount());
    }

    @Test
    public void testSimSeaportTerminal() {
        SimSeaportTerminal seaportTerminal = new SimSeaportTerminal(mockSeaportTerminal);
        SimLiquefactionPlant prevNode = new SimLiquefactionPlant(mockLiquefactionPlant);

        prevNode.deliver(-1000f);

        seaportTerminal.addPreviousNode(prevNode);
        seaportTerminal.simulate(0);

        assertEquals(1000f, seaportTerminal.getToDeliver().getAmount());
        assertTrue(seaportTerminal.shipAbleToDock(), "Debería haber espacio para barcos");

        seaportTerminal.addBoat();
        seaportTerminal.addBoat();
        assertFalse(seaportTerminal.shipAbleToDock(), "La capacidad de barcos debería estar llena");
    }

    @Test
    public void testSimLNGCarrier() {
        SimLNGCarrier carrier = new SimLNGCarrier(mockCarrier);
        SimSeaportTerminal terminal = new SimSeaportTerminal(mockSeaportTerminal);

        terminal.simulate(0);
        terminal.deliver(-5000f);

        carrier.addPreviousNode(terminal);

        carrier.simulate(0);

    }

    @Test
    @Timeout(value = 5)
    public void testSimulationSimpleComplete() {
        List<BaseNode> baseNodes = new ArrayList<>();
        baseNodes.add(mockWell);
        baseNodes.add(mockGatheringNetwork);
        baseNodes.add(mockTreatmentPlant);
        baseNodes.add(mockPipeline);
        baseNodes.add(mockCompressingPlant);
        baseNodes.add(mockLiquefactionPlant);
        baseNodes.add(mockSeaportTerminal);
        baseNodes.add(mockCarrier);

        // Pozo -> Red -> Planta de Tratamiento -> Gasoducto -> Planta Compresora -> Planta Licuefacción -> Terminal Portuaria -> Barco
        List<NodeConnection> connections = new ArrayList<>();
        connections.add(crearConexionMock(idWell, idGn));
        connections.add(crearConexionMock(idGn, idTp));
        connections.add(crearConexionMock(idTp, idPipe));
        connections.add(crearConexionMock(idPipe, idCp));
        connections.add(crearConexionMock(idCp, idLp));
        connections.add(crearConexionMock(idLp, idSt));
        connections.add(crearConexionMock(idSt, idCarrier));

        Simulator simulator = new Simulator(baseNodes, connections);

        long startTime = System.currentTimeMillis();

        int añosASimular = 10;
        simulator.simulate(añosASimular);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Tiempo para procesar la red completa ("+añosASimular +" año): " + duration + " ms");

        assertTrue(duration < 2000, "El simulador tardó demasiado, posible bucle infinito en el While de 'quedanPendientes'");
    }

    private Well crearMockWell(UUID id) {
        Well mock = Mockito.mock(Well.class);
        configurarBaseNode(mock, id);
        when(mock.getMaxCollectionCapacity()).thenReturn(1000f);
        when(mock.getDeclineCurve()).thenReturn(5f);
        when(mock.getGasRichness()).thenReturn(5f);
        return mock;
    }

    private GatheringNetwork crearMockGatheringNetwork(UUID id) {
        GatheringNetwork mock = Mockito.mock(GatheringNetwork.class);
        configurarBaseNode(mock, id);
        when(mock.getMaxTransportCapacity()).thenReturn(5000f);
        when(mock.getLength()).thenReturn(10f);
        when(mock.getLossPerMeter()).thenReturn(0.01f);
        return mock;
    }

    private TreatmentPlant crearMockTreatmentPlant(UUID id) {
        TreatmentPlant mock = Mockito.mock(TreatmentPlant.class);
        configurarBaseNode(mock, id);
        when(mock.getMaxTreatmentCapacity()).thenReturn(3000f);
        when(mock.getIntermediateStorage()).thenReturn(500f);
        return mock;
    }

    private Pipeline crearMockPipeline(UUID id) {
        Pipeline mock = Mockito.mock(Pipeline.class);
        configurarBaseNode(mock, id);
        when(mock.getMaxFlowCapacity()).thenReturn(6000f);
        when(mock.getLength()).thenReturn(100f);
        when(mock.getLossPerKm()).thenReturn(0.05f);
        return mock;
    }

    private CompressingPlant crearMockCompressingPlant(UUID id) {
        CompressingPlant mock = Mockito.mock(CompressingPlant.class);
        configurarBaseNode(mock, id);
        when(mock.getMaxCompressionCapacity()).thenReturn(5000f);
        when(mock.getProcessWaste()).thenReturn(2f);
        when(mock.getGasConsumption()).thenReturn(3f);
        return mock;
    }

    private GroundBasedLiquefactionPlant crearMockLiquefactionPlant(UUID id) {
        GroundBasedLiquefactionPlant mock = Mockito.mock(GroundBasedLiquefactionPlant.class);
        configurarBaseNode(mock, id);
        when(mock.getMaxProcessingCapacity()).thenReturn(3000f);
        when(mock.getGasConsumption()).thenReturn(10f);
        when(mock.getMTPARatio()).thenReturn(50f);
        when(mock.getIntermediateStorage()).thenReturn(10000f);
        return mock;
    }

    private SeaportTerminal crearMockSeaportTerminal(UUID id) {
        SeaportTerminal mock = Mockito.mock(SeaportTerminal.class);
        configurarBaseNode(mock, id);
        when(mock.getIntermediateStorage()).thenReturn(50000f);
        when(mock.getShipCapacity()).thenReturn(2);
        return mock;
    }

    private LNGCarrier crearMockCarrier(UUID id) {
        LNGCarrier mock = Mockito.mock(LNGCarrier.class);
        configurarBaseNode(mock, id);
        when(mock.getShipCapacity()).thenReturn(1000f);
        when(mock.getFullLoadTime()).thenReturn(24f);
        when(mock.getExportFrequency()).thenReturn(10);
        when(mock.getTimeToDestination()).thenReturn(48);
        return mock;
    }

    @Test
    @Timeout(value = 20) // Margen de tiempo suficiente para procesar 30 nodos sobre varios años
    public void testSimulationWhitNNodes() {
        int totalNodosDeseados = 100;
        List<BaseNode> baseNodes = new ArrayList<>();
        List<NodeConnection> connections = new ArrayList<>();
        Map<String, List<UUID>> nodosPorCapa = new HashMap<>();

        // Definimos las capas de la cadena de valor para mantener el flujo directo (DAG)
        String[] capas = {"Pozo", "Red", "Tratamiento", "Pipeline", "Compresion", "Licuefaccion", "Puerto", "Barco"};
        for (String capa : capas) {
            nodosPorCapa.put(capa, new ArrayList<>());
        }

        // 1. Instanciamos los 30 nodos distribuyéndolos de forma proporcional entre capas
        Random random = new Random(42); // Semilla fija para consistencia en las ejecuciones
        for (int i = 0; i < totalNodosDeseados; i++) {
            UUID id = UUID.randomUUID();
            String tipoCapa = capas[i % capas.length];
            nodosPorCapa.get(tipoCapa).add(id);

            switch (tipoCapa) {
                case "Pozo" -> baseNodes.add(crearMockWell(id));
                case "Red" -> baseNodes.add(crearMockGatheringNetwork(id));
                case "Tratamiento" -> baseNodes.add(crearMockTreatmentPlant(id));
                case "Pipeline" -> baseNodes.add(crearMockPipeline(id));
                case "Compresion" -> baseNodes.add(crearMockCompressingPlant(id));
                case "Licuefaccion" -> baseNodes.add(crearMockLiquefactionPlant(id));
                case "Puerto" -> baseNodes.add(crearMockSeaportTerminal(id));
                case "Barco" -> baseNodes.add(crearMockCarrier(id));
            }
        }

        // 2. Conectamos nodos aleatoriamente respetando el orden jerárquico (Capa N -> Capa N+1)
        for (int i = 0; i < capas.length - 1; i++) {
            List<UUID> origen = nodosPorCapa.get(capas[i]);
            List<UUID> destino = nodosPorCapa.get(capas[i + 1]);

            if (origen.isEmpty() || destino.isEmpty()) continue;

            // Garantizamos que cada nodo de destino reciba al menos una conexión entrante
            for (UUID idDestino : destino) {
                UUID idOrigen = origen.get(random.nextInt(origen.size()));
                connections.add(crearConexionMock(idOrigen, idDestino));
            }

            // Agregamos conexiones aleatorias extra para generar bifurcaciones y convergencias complejas
            for (UUID idOrigen : origen) {
                if (random.nextBoolean()) {
                    UUID idDestinoExtra = destino.get(random.nextInt(destino.size()));
                    NodeConnection conn = crearConexionMock(idOrigen, idDestinoExtra);
                    if (connections.stream().noneMatch(c -> c.getFromNodeId().equals(idOrigen) && c.getToNodeId().equals(idDestinoExtra))) {
                        connections.add(conn);
                    }
                }
            }
        }

        // 3. Ejecutamos la simulación
        Simulator simulator = new Simulator(baseNodes, connections);

        long startTime = System.currentTimeMillis();
        int anosASimular = 10;

        assertDoesNotThrow(() -> simulator.simulate(anosASimular), "La simulación falló durante la ejecución de los "+ totalNodosDeseados +" nodos");

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Tiempo para procesar red aleatoria con " + baseNodes.size() + " nodos ("+ anosASimular +" año): " + duration + " ms");
        assertTrue(duration < 8000, "El simulador tardó demasiado con la topología de "+ totalNodosDeseados +" nodos.");
    }

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
        when(mockNode.getMaintenanceIntervalInDays()).thenReturn(180);
        when(mockNode.getMaintenanceDuration()).thenReturn(24);
        when(mockNode.getLifespanInMonths()).thenReturn(120);
    }

    private NodeConnection createConnection(UUID fromId, UUID toId) {
        NodeConnection connection = Mockito.mock(NodeConnection.class);
        when(connection.getFromNodeId()).thenReturn(fromId);
        when(connection.getToNodeId()).thenReturn(toId);
        return connection;
    }

    @Test
    public void testSimWellProductionAndDecline() {
        UUID wellId = UUID.randomUUID();
        Well mockWell = Mockito.mock(Well.class);
        when(mockWell.getId()).thenReturn(wellId);
        when(mockWell.getMaxCollectionCapacity()).thenReturn(100f);
        when(mockWell.getDeclineCurve()).thenReturn(10f); // 10% de declive por año
        when(mockWell.getGasRichness()).thenReturn(100f);
        when(mockWell.getDTMTime()).thenReturn(24);
        when(mockWell.getMaintenanceIntervalInDays()).thenReturn(9999); // Sin mantenimiento para simplicidad
        when(mockWell.getLifespanInMonths()).thenReturn(240);

        SimWell simWell = new SimWell(mockWell);

        // Simular el primer año (8760 horas) -> Declive = 0% -> Produce 100 por hora
        for (int t = 0; t < 8760; t++) {
            simWell.simulate(t);
        }
        // Año 0: 8760 horas * 100 = 876,000
        assertEquals(876000f, simWell.createResult().getMaxPossibleProduced(), 0.01f);

        // Simular el segundo año (horas 8760 a 17519) -> Declive = 10% -> Produce 90 por hora
        for (int t = 8760; t < 17520; t++) {
            simWell.simulate(t);
        }
        // Año 1: 8760 horas * 90 = 788,400. Total acumulado = 876,000 + 788,400 = 1,664,400
        assertEquals(1664400f, simWell.createResult().getMaxPossibleProduced(), 0.01f);
    }
}

