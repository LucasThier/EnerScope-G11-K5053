package org.enerscope.simulator;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
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
    @Timeout(value = 10)
    public void testSimulacionRedCompleta() {
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

    @Test
    @Timeout(value = 15)
    public void testSimulacionRedComplejaBifurcada() {

        UUID idW1 = UUID.randomUUID(), idW2 = UUID.randomUUID(), idW3 = UUID.randomUUID();
        UUID idGn1 = UUID.randomUUID(), idGn2 = UUID.randomUUID();
        UUID idTp1 = UUID.randomUUID(), idTp2 = UUID.randomUUID();
        UUID idPipe1 = UUID.randomUUID(), idPipe2 = UUID.randomUUID();
        UUID idCp = UUID.randomUUID();
        UUID idLp1 = UUID.randomUUID(), idLp2 = UUID.randomUUID();
        UUID idSt1 = UUID.randomUUID(), idSt2 = UUID.randomUUID();
        UUID idC1 = UUID.randomUUID(), idC2 = UUID.randomUUID(), idC3 = UUID.randomUUID(), idC4 = UUID.randomUUID();

        Well w1 = crearMockWell(idW1);
        Well w2 = crearMockWell(idW2);
        Well w3 = crearMockWell(idW3);

        GatheringNetwork gn1 = crearMockGatheringNetwork(idGn1);
        GatheringNetwork gn2 = crearMockGatheringNetwork(idGn2);

        TreatmentPlant tp1 = crearMockTreatmentPlant(idTp1);
        TreatmentPlant tp2 = crearMockTreatmentPlant(idTp2);

        Pipeline pipe1 = crearMockPipeline(idPipe1);
        Pipeline pipe2 = crearMockPipeline(idPipe2);

        CompressingPlant cp = crearMockCompressingPlant(idCp);

        GroundBasedLiquefactionPlant lp1 = crearMockLiquefactionPlant(idLp1);
        GroundBasedLiquefactionPlant lp2 = crearMockLiquefactionPlant(idLp2);

        SeaportTerminal st1 = crearMockSeaportTerminal(idSt1);
        SeaportTerminal st2 = crearMockSeaportTerminal(idSt2);

        LNGCarrier c1 = crearMockCarrier(idC1);
        LNGCarrier c2 = crearMockCarrier(idC2);
        LNGCarrier c3 = crearMockCarrier(idC3);
        LNGCarrier c4 = crearMockCarrier(idC4);

        List<BaseNode> baseNodes = Arrays.asList(
                w1, w2, w3, gn1, gn2, tp1, tp2, pipe1, pipe2, cp, lp1, lp2, st1, st2, c1, c2, c3, c4
        );

        List<NodeConnection> connections = Arrays.asList(

                crearConexionMock(idW1, idGn1),
                crearConexionMock(idW2, idGn1),
                crearConexionMock(idGn1, idTp1),

                crearConexionMock(idW3, idGn2),
                crearConexionMock(idGn2, idTp2),

                crearConexionMock(idTp1, idPipe1),
                crearConexionMock(idTp2, idPipe1),
                crearConexionMock(idPipe1, idCp),

                crearConexionMock(idCp, idLp1),
                crearConexionMock(idCp, idPipe2),

                crearConexionMock(idPipe2, idLp2),

                crearConexionMock(idLp1, idSt1),
                crearConexionMock(idLp2, idSt2),

                crearConexionMock(idSt1, idC1),
                crearConexionMock(idSt1, idC2),
                crearConexionMock(idSt2, idC3),
                crearConexionMock(idSt2, idC4)
        );

        Simulator simulator = new Simulator(baseNodes, connections);

        long startTime = System.currentTimeMillis();
        int anosASimular = 10;
        simulator.simulate(anosASimular);
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("Tiempo para procesar la red compleja ("+anosASimular+" años): " + duration + " ms");
        assertTrue(duration < 5000, "El simulador tardó demasiado, revisa la lógica de 'readyToBeProcessed' con múltiples nodos del mismo tipo.");
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
    public void testSimulacionRedAleatoria30Nodos() {
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

        assertDoesNotThrow(() -> simulator.simulate(anosASimular), "La simulación falló durante la ejecución de los 30 nodos");

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Tiempo para procesar red aleatoria con " + baseNodes.size() + " nodos (1 año): " + duration + " ms");
        assertTrue(duration < 8000, "El simulador tardó demasiado con la topología de 30 nodos.");
    }
}
