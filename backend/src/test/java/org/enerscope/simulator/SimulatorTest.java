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
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.simulator.auxiliary.ToDeliver;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.results.ResultPerRound;
import org.enerscope.simulator.simNode.*;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SimulatorTest {

    private Well mockWell;
    private GatheringNetwork mockGatheringNetwork;
    private TreatmentPlant mockTreatmentPlant;
    private Pipeline mockPipeline;
    private PipelineConnection mockPipelineConnection;
    private CompressingPlant mockCompressingPlant;
    private GroundBasedLiquefactionPlant mockLiquefactionPlant;
    private SeaportTerminal mockSeaportTerminal;
    private LNGCarrier mockCarrier;

    private UUID idWell, idGn, idTp, idPipe, idCp, idLp, idSt, idCarrier, idPipeConn;

    @BeforeEach
    void setUp() {
        idWell = UUID.randomUUID();
        idGn = UUID.randomUUID();
        idTp = UUID.randomUUID();
        idPipe = UUID.randomUUID();
        idPipeConn = UUID.randomUUID();
        idCp = UUID.randomUUID();
        idLp = UUID.randomUUID();
        idSt = UUID.randomUUID();
        idCarrier = UUID.randomUUID();

        mockWell = createMockNode(Well.class, idWell, 365, 24, 240, w -> {
            when(w.getMaxCollectionCapacity()).thenReturn(1000f);
            when(w.getDeclineCurve()).thenReturn(new ConstantValue(5f));
            when(w.getGasRichness()).thenReturn(50f);
        });

        mockGatheringNetwork = createMockNode(GatheringNetwork.class, idGn, 365, 24, 240, gn -> {
            when(gn.getMaxTransportCapacity()).thenReturn(5000f);
            when(gn.getLength()).thenReturn(10f);
            when(gn.getLossPerMeter()).thenReturn(0.01f);
        });

        mockTreatmentPlant = createMockNode(TreatmentPlant.class, idTp, 365, 24, 240, tp -> {
            when(tp.getMaxTreatmentCapacity()).thenReturn(2000f);
            when(tp.getIntermediateStorage()).thenReturn(500f);
        });

        mockPipeline = createMockNode(Pipeline.class, idPipe, 365, 24, 240, p -> {
            when(p.getMaxFlowCapacity()).thenReturn(3000f);
            when(p.getLength()).thenReturn(100f);
            when(p.getLossPerKm()).thenReturn(0.05f);
        });

        mockPipelineConnection = createMockNode(PipelineConnection.class, idPipeConn, 365, 24, 240, pc -> {
            when(pc.getTransferCapacity()).thenReturn(3000f);
            when(pc.getOutputPriority()).thenReturn(1f);
        });

        mockCompressingPlant = createMockNode(CompressingPlant.class, idCp, 365, 24, 240, cp -> {
            when(cp.getMaxCompressionCapacity()).thenReturn(2500f);
            when(cp.getProcessWaste()).thenReturn(2f);
            when(cp.getGasConsumption()).thenReturn(3f);
        });

        mockLiquefactionPlant = createMockNode(GroundBasedLiquefactionPlant.class, idLp, 365, 24, 240, lp -> {
            when(lp.getMaxProcessingCapacity()).thenReturn(2000f);
            when(lp.getGasConsumption()).thenReturn(10f);
            when(lp.getMTPARatio()).thenReturn(50f);
            when(lp.getIntermediateStorage()).thenReturn(10000f);
        });

        mockSeaportTerminal = createMockNode(SeaportTerminal.class, idSt, 365, 24, 240, st -> {
            when(st.getIntermediateStorage()).thenReturn(50000f);
            when(st.getShipCapacity()).thenReturn(2);
        });

        mockCarrier = createMockNode(LNGCarrier.class, idCarrier, 365, 24, 240, c -> {
            when(c.getShipCapacity()).thenReturn(1000f);
            when(c.getFullLoadTime()).thenReturn(24f);
            when(c.getExportFrequency()).thenReturn(10);
            when(c.getTimeToDestination()).thenReturn(48);
        });
    }

    /**
     * Método fábrica genérico para instanciar y configurar cualquier BaseNode mock.
     */
    private <T extends BaseNode> T createMockNode(Class<T> clazz, UUID id, int maintenanceInterval, int maintenanceDuration, int lifespan, Consumer<T> customConfig) {
        T mockNode = mock(clazz);
        when(mockNode.getId()).thenReturn(id);
        when(mockNode.getMaintenanceIntervalInDays()).thenReturn(maintenanceInterval);
        when(mockNode.getMaintenanceDuration()).thenReturn(maintenanceDuration);
        when(mockNode.getLifespanInMonths()).thenReturn(lifespan);
        if (customConfig != null) {
            customConfig.accept(mockNode);
        }
        return mockNode;
    }

    private NodeConnection crearConexionMock(UUID from, UUID to) {
        NodeConnection connection = mock(NodeConnection.class);
        when(connection.getFromNodeId()).thenReturn(from);
        when(connection.getToNodeId()).thenReturn(to);
        return connection;
    }

    // --- Tests de Unidad ---

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

        assertEquals(999f, network.getToDeliver().getAmount());
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
    public void testSimConnection() {
        SimPipelineConnection pipelineConnection = new SimPipelineConnection(mockPipelineConnection);
        SimTreatmentPlant prevNode = new SimTreatmentPlant(mockTreatmentPlant);

        prevNode.deliver(-1000f);
        pipelineConnection.addPreviousNode(prevNode);
        pipelineConnection.simulate(0);

        assertEquals(1000f, pipelineConnection.getToDeliver().getAmount(), "El cálculo de flujo en el Pipeline falló");
    }

    @Test
    public void testSimPipeline() {
        SimPipeline pipeline = new SimPipeline(mockPipeline);
        SimTreatmentPlant prevNode = new SimTreatmentPlant(mockTreatmentPlant);

        prevNode.deliver(-1000f);
        pipeline.addPreviousNode(prevNode);
        pipeline.simulate(0);
        pipeline.simulate(1);

        assertEquals(950f, pipeline.getToDeliver().getAmount(), "El cálculo de flujo en el Pipeline falló");
    }

    @Test
    public void testSimPipelineConnection() {
        SimPipelineConnection pipelineConnection = new SimPipelineConnection(mockPipelineConnection);
        SimPipeline prevNode = new SimPipeline(mockPipeline);

        prevNode.deliver(-1000f);
        pipelineConnection.addPreviousNode(prevNode);
        pipelineConnection.simulate(0);

        assertEquals(1000f, pipelineConnection.getToDeliver().getAmount(), "El cálculo de flujo en el Pipeline falló");
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
        SimGroundBasedLiquefactionPlant liquefactionPlant = new SimGroundBasedLiquefactionPlant(mockLiquefactionPlant);
        SimCompressingPlant prevNode = new SimCompressingPlant(mockCompressingPlant);

        prevNode.deliver(-1000f);
        liquefactionPlant.addPreviousNode(prevNode);
        liquefactionPlant.simulate(0);

        assertEquals(450f, liquefactionPlant.getToDeliver().getAmount());
    }

    @Test
    public void testSimSeaportTerminal() {
        SimSeaportTerminal seaportTerminal = new SimSeaportTerminal(mockSeaportTerminal);
        SimGroundBasedLiquefactionPlant prevNode = new SimGroundBasedLiquefactionPlant(mockLiquefactionPlant);

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
        List<BaseNode> baseNodes = Arrays.asList(
                mockWell, mockGatheringNetwork, mockTreatmentPlant, mockPipeline,
                mockCompressingPlant, mockLiquefactionPlant, mockSeaportTerminal, mockCarrier
        );

        List<NodeConnection> connections = Arrays.asList(
                crearConexionMock(idWell, idGn),
                crearConexionMock(idGn, idTp),
                crearConexionMock(idTp, idPipe),
                crearConexionMock(idPipe, idCp),
                crearConexionMock(idCp, idLp),
                crearConexionMock(idLp, idSt),
                crearConexionMock(idSt, idCarrier)
        );

        Version mockVersion = mock(Version.class);
        when(mockVersion.getNodeSnapshot()).thenReturn(baseNodes);
        when(mockVersion.getConnectionSnapshot()).thenReturn(connections);

        Simulator simulator = new Simulator(mockVersion);

        long startTime = System.currentTimeMillis();
        int añosASimular = 10;
        simulator.simulate(añosASimular);

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Tiempo para procesar la red completa (" + añosASimular + " año): " + duration + " ms");

        assertTrue(duration < 2000, "El simulador tardó demasiado, posible bucle infinito.");
    }

    // --- Métodos de fábrica simplificados para tests de volumen/redes N ---

    private Well crearMockWell(UUID id) {
        return createMockNode(Well.class, id, 365, 24, 240, w -> {
            when(w.getMaxCollectionCapacity()).thenReturn(1000f);
            when(w.getDeclineCurve()).thenReturn(new ConstantValue(5f));
            when(w.getGasRichness()).thenReturn(5f);
            when(w.getDTMTime()).thenReturn(24);
        });
    }

    private GatheringNetwork crearMockGatheringNetwork(UUID id) {
        return createMockNode(GatheringNetwork.class, id, 365, 24, 240, gn -> {
            when(gn.getMaxTransportCapacity()).thenReturn(5000f);
            when(gn.getLength()).thenReturn(10f);
            when(gn.getLossPerMeter()).thenReturn(0.01f);
        });
    }

    private Pipeline crearMockPipeline(UUID id) {
        return createMockNode(Pipeline.class, id, 365, 24, 240, p -> {
            when(p.getMaxFlowCapacity()).thenReturn(6000f);
            when(p.getLength()).thenReturn(100f);
            when(p.getLossPerKm()).thenReturn(0.05f);
        });
    }

    private PipelineConnection crearMockPipelineConnection(UUID id) {
        return createMockNode(PipelineConnection.class, id, 365, 24, 240, pc -> {
            when(pc.getTransferCapacity()).thenReturn(3000f);
            when(pc.getOutputPriority()).thenReturn(1f);
        });
    }

    private CompressingPlant crearMockCompressingPlant(UUID id) {
        return createMockNode(CompressingPlant.class, id, 365, 24, 240, cp -> {
            when(cp.getMaxCompressionCapacity()).thenReturn(5000f);
            when(cp.getProcessWaste()).thenReturn(2f);
            when(cp.getGasConsumption()).thenReturn(3f);
        });
    }

    private GroundBasedLiquefactionPlant crearMockLiquefactionPlant(UUID id) {
        return createMockNode(GroundBasedLiquefactionPlant.class, id, 365, 24, 240, lp -> {
            when(lp.getMaxProcessingCapacity()).thenReturn(3000f);
            when(lp.getGasConsumption()).thenReturn(10f);
            when(lp.getMTPARatio()).thenReturn(50f);
            when(lp.getIntermediateStorage()).thenReturn(10000f);
        });
    }

    private FLNGUnit crearMockFLNG(UUID id) {
        return createMockNode(FLNGUnit.class, id, 365, 24, 240, flng -> {
            when(flng.getMaxProcessingCapacity()).thenReturn(80f);
            when(flng.getMTPARatio()).thenReturn(85f);
            when(flng.getIntermediateStorage()).thenReturn(250f);
            when(flng.getGasConsumption()).thenReturn(1.2f);
        });
    }

    private SeaportTerminal crearMockSeaportTerminal(UUID id) {
        return createMockNode(SeaportTerminal.class, id, 365, 24, 240, st -> {
            when(st.getIntermediateStorage()).thenReturn(50000f);
            when(st.getShipCapacity()).thenReturn(2);
        });
    }

    private LNGCarrier crearMockCarrier(UUID id) {
        return createMockNode(LNGCarrier.class, id, 365, 24, 240, c -> {
            when(c.getShipCapacity()).thenReturn(1000f);
            when(c.getFullLoadTime()).thenReturn(24f);
            when(c.getExportFrequency()).thenReturn(10);
            when(c.getTimeToDestination()).thenReturn(48);
        });
    }

    @Test
    @Timeout(value = 20)
    public void testSimulationWhitNNodes() {
        int totalNodosDeseados = 100;
        List<BaseNode> baseNodes = new ArrayList<>();
        List<NodeConnection> connections = new ArrayList<>();
        Map<String, List<UUID>> nodosPorCapa = new HashMap<>();

        String[] capas = {"Pozo", "Red", "CompresionInicial", "PipelineMix", "Licuefaccion", "Puerto", "Barco"};
        for (String capa : capas) {
            nodosPorCapa.put(capa, new ArrayList<>());
        }

        Random random = new Random(42);
        for (int i = 0; i < totalNodosDeseados; i++) {
            UUID id = UUID.randomUUID();
            String tipoCapa = capas[i % capas.length];
            nodosPorCapa.get(tipoCapa).add(id);

            switch (tipoCapa) {
                case "Pozo" -> baseNodes.add(crearMockWell(id));
                case "Red" -> baseNodes.add(crearMockGatheringNetwork(id));
                case "CompresionInicial" -> baseNodes.add(crearMockCompressingPlant(id));
                case "PipelineMix" -> {
                    int subType = random.nextInt(3);
                    if (subType == 0) baseNodes.add(crearMockPipeline(id));
                    else if (subType == 1) baseNodes.add(crearMockPipelineConnection(id));
                    else baseNodes.add(crearMockCompressingPlant(id));
                }
                case "Licuefaccion" -> {
                    if (random.nextBoolean()) baseNodes.add(crearMockLiquefactionPlant(id));
                    else baseNodes.add(crearMockFLNG(id));
                }
                case "Puerto" -> baseNodes.add(crearMockSeaportTerminal(id));
                case "Barco" -> baseNodes.add(crearMockCarrier(id));
            }
        }

        for (int i = 0; i < capas.length - 1; i++) {
            String capaActual = capas[i];
            String capaSiguiente = capas[i + 1];

            if (capaActual.equals("Licuefaccion")) continue;

            List<UUID> origen = nodosPorCapa.get(capaActual);
            List<UUID> destino = nodosPorCapa.get(capaSiguiente);

            if (origen.isEmpty() || destino.isEmpty()) continue;

            for (UUID idDestino : destino) {
                UUID idOrigen = origen.get(random.nextInt(origen.size()));
                NodeConnection conn = crearConexionMock(idOrigen, idDestino);
                if (connections.stream().noneMatch(c -> c.getFromNodeId().equals(idOrigen) && c.getToNodeId().equals(idDestino))) {
                    connections.add(conn);
                }
            }
        }

        List<UUID> licuefaccionNodes = nodosPorCapa.get("Licuefaccion");
        List<UUID> puertoNodes = nodosPorCapa.get("Puerto");
        List<UUID> barcoNodes = nodosPorCapa.get("Barco");
        List<UUID> pipelineMixNodes = nodosPorCapa.get("PipelineMix");

        if (!puertoNodes.isEmpty() && !barcoNodes.isEmpty()) {
            for (UUID idPuerto : puertoNodes) {
                UUID idBarco = barcoNodes.get(random.nextInt(barcoNodes.size()));
                connections.add(crearConexionMock(idPuerto, idBarco));
            }
        }

        for (UUID idLic : licuefaccionNodes) {
            BaseNode nodoLic = baseNodes.stream().filter(n -> n.getId().equals(idLic)).findFirst().orElse(null);

            if (!pipelineMixNodes.isEmpty()) {
                UUID idPrev = pipelineMixNodes.get(random.nextInt(pipelineMixNodes.size()));
                if (connections.stream().noneMatch(c -> c.getFromNodeId().equals(idPrev) && c.getToNodeId().equals(idLic))) {
                    connections.add(crearConexionMock(idPrev, idLic));
                }
            }

            if (nodoLic instanceof GroundBasedLiquefactionPlant && !puertoNodes.isEmpty()) {
                UUID idPuerto = puertoNodes.get(random.nextInt(puertoNodes.size()));
                connections.add(crearConexionMock(idLic, idPuerto));
            } else if (nodoLic instanceof FLNGUnit && !barcoNodes.isEmpty()) {
                UUID idBarco = barcoNodes.get(random.nextInt(barcoNodes.size()));
                connections.add(crearConexionMock(idLic, idBarco));
            }
        }

        Version mockVersion = mock(Version.class);
        when(mockVersion.getNodeSnapshot()).thenReturn(baseNodes);
        when(mockVersion.getConnectionSnapshot()).thenReturn(connections);

        Simulator simulator = new Simulator(mockVersion);

        long startTime = System.currentTimeMillis();
        int anosASimular = 10;

        assertDoesNotThrow(() -> simulator.simulate(anosASimular), "La simulación falló con " + totalNodosDeseados + " nodos");

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Tiempo para procesar red aleatoria con " + baseNodes.size() + " nodos: " + duration + " ms");
        assertTrue(duration < 8000, "El simulador tardó demasiado.");
    }

    @Test
    public void testResultInFiveYearSimulationWithAllNodeTypes() {
        UUID wellId = UUID.randomUUID();
        UUID gatheringId = UUID.randomUUID();
        UUID compressionId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID pipelineConnectionId = UUID.randomUUID();
        UUID flngLiquefactionId = UUID.randomUUID();
        UUID groundLiquefactionId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();

        List<BaseNode> nodes = Arrays.asList(
                createMockNode(Well.class, wellId, 180, 24, 120, w -> {
                    when(w.getMaxCollectionCapacity()).thenReturn(100f);
                    when(w.getDeclineCurve()).thenReturn(new ConstantValue(2f));
                    when(w.getGasRichness()).thenReturn(1f);
                    when(w.getDTMTime()).thenReturn(24);
                }),
                createMockNode(GatheringNetwork.class, gatheringId, 180, 24, 120, g -> {
                    when(g.getMaxTransportCapacity()).thenReturn(150f);
                    when(g.getLength()).thenReturn(5f);
                    when(g.getLossPerMeter()).thenReturn(0.01f);
                }),
                createMockNode(CompressingPlant.class, compressionId, 180, 24, 120, c -> {
                    when(c.getMaxCompressionCapacity()).thenReturn(100f);
                    when(c.getProcessWaste()).thenReturn(1f);
                    when(c.getGasConsumption()).thenReturn(2f);
                }),
                createMockNode(Pipeline.class, pipelineId, 180, 24, 120, p -> {
                    when(p.getMaxFlowCapacity()).thenReturn(110f);
                    when(p.getLength()).thenReturn(12f);
                    when(p.getLossPerKm()).thenReturn(0.05f);
                }),
                createMockNode(PipelineConnection.class, pipelineConnectionId, 180, 24, 120, pc -> {
                    when(pc.getTransferCapacity()).thenReturn(110f);
                    when(pc.getOutputPriority()).thenReturn(1f);
                }),
                createMockNode(FLNGUnit.class, flngLiquefactionId, 180, 24, 120, f -> {
                    when(f.getMaxProcessingCapacity()).thenReturn(80f);
                    when(f.getMTPARatio()).thenReturn(85f);
                    when(f.getIntermediateStorage()).thenReturn(250f);
                    when(f.getGasConsumption()).thenReturn(1.2f);
                }),
                createMockNode(GroundBasedLiquefactionPlant.class, groundLiquefactionId, 180, 24, 120, gl -> {
                    when(gl.getMaxProcessingCapacity()).thenReturn(90f);
                    when(gl.getMTPARatio()).thenReturn(80f);
                    when(gl.getIntermediateStorage()).thenReturn(300f);
                    when(gl.getGasConsumption()).thenReturn(1.5f);
                }),
                createMockNode(SeaportTerminal.class, terminalId, 180, 24, 120, st -> {
                    when(st.getIntermediateStorage()).thenReturn(1000f);
                    when(st.getShipCapacity()).thenReturn(2);
                }),
                createMockNode(LNGCarrier.class, carrierId, 180, 24, 120, ca -> {
                    when(ca.getExportFrequency()).thenReturn(7);
                    when(ca.getShipCapacity()).thenReturn(500f);
                    when(ca.getFullLoadTime()).thenReturn(24f);
                    when(ca.getTimeToDestination()).thenReturn(72);
                })
        );

        List<NodeConnection> connections = Arrays.asList(
                crearConexionMock(wellId, gatheringId),
                crearConexionMock(gatheringId, compressionId),
                crearConexionMock(compressionId, pipelineId),
                crearConexionMock(pipelineId, pipelineConnectionId),
                crearConexionMock(pipelineConnectionId, flngLiquefactionId),
                crearConexionMock(pipelineId, groundLiquefactionId),
                crearConexionMock(groundLiquefactionId, terminalId),
                crearConexionMock(terminalId, carrierId)
        );

        Version mockVersion = mock(Version.class);
        when(mockVersion.getNodeSnapshot()).thenReturn(nodes);
        when(mockVersion.getConnectionSnapshot()).thenReturn(connections);

        Simulator simulator = new Simulator(mockVersion);
        simulator.simulate(5);

        ResultPerRound resultPerRound = simulator.getResultsPerRound().get(1);
        List<ResultPerNode> nodeResults = resultPerRound.getResultPerNodes();

        assertNotNull(resultPerRound);
        assertEquals(5, simulator.getFinalResult().getTime());
        assertEquals(9, nodeResults.size());

        nodeResults.forEach(nodeResult -> {
            assertNotNull(nodeResult.getNodeID());
            assertTrue(nodeResult.getMaxPossibleProduced() >= 0);
            assertTrue(nodeResult.getTotalProduced() >= 0);
            assertTrue(nodeResult.getTotalDeferred() >= 0);
        });
    }

    @Test
    public void testSimWellProductionAndDecline() {
        UUID wellId = UUID.randomUUID();
        Well mockWell = createMockNode(Well.class, wellId, 9999, 24, 240, w -> {
            when(w.getMaxCollectionCapacity()).thenReturn(100f);
            when(w.getDeclineCurve()).thenReturn(new ConstantValue(10f));
            when(w.getGasRichness()).thenReturn(100f);
            when(w.getDTMTime()).thenReturn(24);
        });

        SimWell simWell = new SimWell(mockWell);

        for (int t = 0; t < 8760; t++) {
            simWell.simulate(t);
        }
        assertEquals(876000f, simWell.createResult().getMaxPossibleProduced(), 0.01f);

        for (int t = 8760; t < 17520; t++) {
            simWell.simulate(t);
        }
        assertEquals(1664400f, simWell.createResult().getMaxPossibleProduced(), 0.01f);
    }
}