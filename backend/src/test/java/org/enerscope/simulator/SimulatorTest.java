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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        when(mockGatheringNetwork.getMaxTransportCapacity()).thenReturn(5000f);
        when(mockGatheringNetwork.getLength()).thenReturn(10f);
        when(mockGatheringNetwork.getLossPerMeter()).thenReturn(0.01f);

        when(mockTreatmentPlant.getMaxTreatmentCapacity()).thenReturn(2000f);
        when(mockTreatmentPlant.getContaminantWaste()).thenReturn(5f);

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
        assertEquals(1000f, simWell.getToDeliver(), "Hora 0 debe ser 1000f");

        int unAnoEnHoras = 24 * 365;
        simWell.simulate(unAnoEnHoras);

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
    public void testSimTreatmentPlant() {
        SimTreatmentPlant treatmentPlant = new SimTreatmentPlant(mockTreatmentPlant);
        SimGatheringNetwork network = new SimGatheringNetwork(mockGatheringNetwork);

        network.deliver(-1000f);

        treatmentPlant.addPreviousNode(network);
        treatmentPlant.simulate(0);

        assertEquals(950f, treatmentPlant.getToDeliver(), "El cálculo del TreatmentPlant con el contaminantWaste falló");
    }

    @Test
    public void testSimPipeline() {
        SimPipeline pipeline = new SimPipeline(mockPipeline);
        SimTreatmentPlant prevNode = new SimTreatmentPlant(mockTreatmentPlant);

        prevNode.deliver(-1000f);
        pipeline.addPreviousNode(prevNode);

        pipeline.simulate(0);

        assertEquals(950f, pipeline.getToDeliver(), "El cálculo de flujo en el Pipeline falló");
    }

    @Test
    public void testSimCompressingPlant() {
        SimCompressingPlant compressingPlant = new SimCompressingPlant(mockCompressingPlant);
        SimPipeline prevNode = new SimPipeline(mockPipeline);

        prevNode.deliver(-1000f);

        compressingPlant.addPreviousNode(prevNode);

        compressingPlant.simulate(0);

        assertEquals(950f, compressingPlant.getToDeliver(), "El cálculo de compresión falló");
    }

    @Test
    public void testSimLiquefactionPlant() {
        SimLiquefactionPlant liquefactionPlant = new SimLiquefactionPlant(mockLiquefactionPlant);
        SimCompressingPlant prevNode = new SimCompressingPlant(mockCompressingPlant);

        prevNode.deliver(-1000f);
        liquefactionPlant.addPreviousNode(prevNode);

        liquefactionPlant.simulate(0);

        assertEquals(450f, liquefactionPlant.getToDeliver());
    }

    @Test
    public void testSimSeaportTerminal() {
        SimSeaportTerminal seaportTerminal = new SimSeaportTerminal(mockSeaportTerminal);
        SimLiquefactionPlant prevNode = new SimLiquefactionPlant(mockLiquefactionPlant);

        prevNode.deliver(-1000f);

        seaportTerminal.addPreviousNode(prevNode);
        seaportTerminal.simulate(0);

        assertEquals(1000f, seaportTerminal.getToDeliver());
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

        System.out.println("Tiempo para procesar la red completa (1 año): " + duration + " ms");

        assertTrue(duration < 2000, "El simulador tardó demasiado, posible bucle infinito en el While de 'quedanPendientes'");
    }
}
