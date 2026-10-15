package org.enerscope.simulator;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.results.ResultPerRound;
import org.enerscope.simulator.simNode.SimBaseNode;
import org.enerscope.simulator.simNode.SimWell;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for what the simulator met once it was fed real models from
 * the editor: soft-deleted nodes, wells without a decline curve, upstream node
 * types the engine did not expect, cycles, and Monte Carlo rounds that leaked
 * state into one another.
 */
class SimulatorRobustnessTest {

    private static <T extends BaseNode> T node(T node, NodeStateEnum state) {
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        node.setName(node.getClass().getSimpleName());
        node.setState(state);
        node.setLifespanInMonths(240);
        // Long enough that no maintenance stop falls inside the short horizons
        // used here, so the expected volumes are plain capacity × hours.
        node.setMaintenanceIntervalInDays(3650);
        node.setMaintenanceDuration(24);
        return node;
    }

    private static Well well(NodeStateEnum state) {
        Well well = node(new Well(), state);
        well.setMaxCollectionCapacity(100f);
        well.setDeclineCurve(new ConstantValue(0f));
        well.setGasRichness(0f);
        well.setDTMTime(24);
        return well;
    }

    private static GatheringNetwork gathering() {
        GatheringNetwork network = node(new GatheringNetwork(), NodeStateEnum.RUNNING);
        network.setMaxTransportCapacity(1000f);
        network.setLength(1f);
        network.setLossPerMeter(0f);
        return network;
    }

    private static TreatmentPlant treatment() {
        TreatmentPlant plant = node(new TreatmentPlant(), NodeStateEnum.RUNNING);
        plant.setMaxTreatmentCapacity(1000f);
        plant.setIntermediateStorage(0f);
        return plant;
    }

    private static Pipeline pipeline() {
        Pipeline pipeline = node(new Pipeline(), NodeStateEnum.RUNNING);
        pipeline.setMaxFlowCapacity(1000f);
        pipeline.setLength(1f);
        pipeline.setLossPerKm(0f);
        return pipeline;
    }

    private static InternalConsumption consumption(float perHour) {
        InternalConsumption consumption = node(new InternalConsumption(), NodeStateEnum.RUNNING);
        consumption.setConsumptionSummer(perHour);
        consumption.setConsumptionAutumn(perHour);
        consumption.setConsumptionWinter(perHour);
        consumption.setConsumptionSpring(perHour);
        return consumption;
    }

    private static NodeConnection connect(BaseNode from, BaseNode to) {
        return new NodeConnection(UUID.randomUUID(), from.getId(), to.getId());
    }

    private static Version version(List<BaseNode> nodes, List<NodeConnection> connections) {
        return new Version("test", null, new ArrayList<>(nodes), new ArrayList<>(connections), null, null);
    }

    private static float producedBy(List<ResultPerNode> results, BaseNode node) {
        return results.stream().filter(r -> r.getNodeID().equals(node.getId()))
                .findFirst().orElseThrow().getTotalProduced();
    }

    @Test
    void removedNodesAreLeftOutOfTheSimulation() {
        Well running = well(NodeStateEnum.RUNNING);
        Well removed = well(NodeStateEnum.REMOVED);
        GatheringNetwork network = gathering();
        InternalConsumption consumption = consumption(1000f);

        Simulator simulator = new Simulator(version(
                List.of(running, removed, network, consumption),
                List.of(connect(running, network), connect(removed, network), connect(network, consumption))));

        assertTrue(simulator.getExecutionOrder().stream().map(SimBaseNode::getId).noneMatch(removed.getId()::equals));
        assertTrue(simulator.getNodesOutsideExecutionOrder().isEmpty(),
                "a REMOVED node is excluded on purpose, not reported as unreachable");

        simulator.simulate(1, 1);
        List<ResultPerNode> results = simulator.getFinalResult().getPercentile50();
        assertEquals(3, results.size());
        // Only the running well feeds the network: 100 per hour, not 200.
        assertEquals(100f * (24 * 365 + 1), producedBy(results, consumption), 1f);
    }

    @Test
    void aWellWithoutADeclineCurveKeepsItsCapacity() {
        Well well = well(NodeStateEnum.RUNNING);
        well.setDeclineCurve(null);
        GatheringNetwork network = gathering();

        Simulator simulator = new Simulator(version(List.of(well, network), List.of(connect(well, network))));

        assertDoesNotThrow(() -> simulator.simulate(2, 1));
        ResultPerNode wellResult = simulator.getFinalResult().getPercentile50().stream()
                .filter(r -> r.getNodeID().equals(well.getId())).findFirst().orElseThrow();
        assertEquals(wellResult.getMaxPossibleProduced(), 100f * (2 * 24 * 365 + 1), 1f);
    }

    @Test
    void anyUpstreamTypeIsAcceptedInsteadOfFailingTheSimulation() {
        // Pipeline -> gathering network and pipeline -> treatment plant are not
        // value-chain shapes the engine was written for; they used to throw a
        // ClassCastException while the graph was being built.
        Well well = well(NodeStateEnum.RUNNING);
        Pipeline pipeline = pipeline();
        GatheringNetwork network = gathering();
        TreatmentPlant plant = treatment();
        SeaportTerminal terminal = node(new SeaportTerminal(), NodeStateEnum.RUNNING);
        terminal.setIntermediateStorage(1000f);
        terminal.setShipCapacity(1);

        List<BaseNode> nodes = List.of(well, pipeline, network, plant, terminal);
        List<NodeConnection> connections = List.of(connect(well, pipeline), connect(pipeline, network),
                connect(network, plant), connect(plant, terminal));

        Simulator simulator = assertDoesNotThrow(() -> new Simulator(version(nodes, connections)));
        assertDoesNotThrow(() -> simulator.simulate(1, 1));
    }

    @Test
    void aCarrierWithNoTerminalUpstreamStaysIdle() {
        Pipeline pipeline = pipeline();
        LNGCarrier carrier = node(new LNGCarrier(), NodeStateEnum.RUNNING);
        carrier.setShipCapacity(100f);
        carrier.setFullLoadTime(10f);
        carrier.setExportFrequency(10);
        carrier.setTimeToDestination(10);

        Simulator simulator = new Simulator(version(List.of(pipeline, carrier), List.of(connect(pipeline, carrier))));

        assertDoesNotThrow(() -> simulator.simulate(1, 1));
        assertEquals(0f, producedBy(simulator.getFinalResult().getPercentile50(), carrier));
    }

    @Test
    void nodesOnACycleAreReportedAsOutsideTheExecutionOrder() {
        Pipeline first = pipeline();
        Pipeline second = pipeline();
        Well well = well(NodeStateEnum.RUNNING);

        Simulator simulator = new Simulator(version(List.of(well, first, second),
                List.of(connect(first, second), connect(second, first))));

        assertEquals(1, simulator.getExecutionOrder().size());
        assertEquals(List.of(first.getId(), second.getId()), simulator.getNodesOutsideExecutionOrder());
    }

    @Test
    void roundsDoNotLeakStateIntoOneAnother() {
        // With constant inputs every round is the same experiment, so every round
        // must deliver the same amount. Before each round got a graph of its own,
        // reset() left the pipeline's in-transit batches behind: stamped with the
        // previous round's late arrival times, they never arrived and kept the
        // pipeline's capacity taken, so every later round delivered nothing.
        Well well = well(NodeStateEnum.RUNNING);
        GatheringNetwork network = gathering();
        Pipeline pipeline = pipeline();
        pipeline.setMaxFlowCapacity(100f);   // exactly the well's output
        pipeline.setLength(140f);            // two hours in transit
        GroundBasedLiquefactionPlant plant = node(new GroundBasedLiquefactionPlant(), NodeStateEnum.RUNNING);
        plant.setMaxProcessingCapacity(1000f);
        plant.setMTPARatio(100f);
        plant.setIntermediateStorage(10000f);
        plant.setGasConsumption(0f);
        SeaportTerminal terminal = node(new SeaportTerminal(), NodeStateEnum.RUNNING);
        terminal.setIntermediateStorage(10000f);
        terminal.setShipCapacity(1);
        LNGCarrier carrier = node(new LNGCarrier(), NodeStateEnum.RUNNING);
        carrier.setShipCapacity(500f);
        carrier.setFullLoadTime(10f);
        carrier.setExportFrequency(24);
        carrier.setTimeToDestination(48);

        Simulator simulator = new Simulator(version(List.of(well, network, pipeline, plant, terminal, carrier),
                List.of(connect(well, network), connect(network, pipeline), connect(pipeline, plant),
                        connect(plant, terminal), connect(terminal, carrier))));
        simulator.simulate(2, 4);

        List<ResultPerRound> rounds = simulator.getResultsPerRound();
        assertEquals(4, rounds.size());
        float first = rounds.get(0).amountDelivered();
        assertTrue(first > 0, "the chain should export something");
        rounds.forEach(round -> assertEquals(first, round.amountDelivered(), 0.01f));
        assertEquals(first, simulator.getFinalResult().getMediaOutput(), 0.01f);
    }

    @Test
    void percentilesNeverShareResultRows() {
        Well well = well(NodeStateEnum.RUNNING);
        InternalConsumption consumption = consumption(10f);

        Simulator simulator = new Simulator(version(List.of(well, consumption), List.of(connect(well, consumption))));
        simulator.simulate(1, 2);

        FinalResult result = simulator.getFinalResult();
        assertEquals(2, result.getRounds());
        assertEquals(1, result.getTime());
        // With two rounds P50 and P10 both land on the lower one; the rows must
        // still be distinct objects, because one row cannot sit in two of the
        // final result's collections.
        for (ResultPerNode row : result.getPercentile50()) {
            assertTrue(result.getPercentile10().stream().noneMatch(other -> other == row));
            assertTrue(result.getPercentile90().stream().noneMatch(other -> other == row));
        }
    }

    @Test
    void simulateRejectsAnEmptyHorizonOrNoRounds() {
        Simulator simulator = new Simulator(version(List.of(well(NodeStateEnum.RUNNING)), List.of()));

        assertThrows(IllegalArgumentException.class, () -> simulator.simulate(0, 10));
        assertThrows(IllegalArgumentException.class, () -> simulator.simulate(1, 0));
    }

    @Test
    void simulateAddsTheFinalResultToTheVersion() {
        Version version = version(List.of(well(NodeStateEnum.RUNNING)), List.of());
        Simulator simulator = new Simulator(version);

        simulator.simulate(1, 1);

        assertEquals(List.of(simulator.getFinalResult()), version.getFinalResults());
    }

    @Test
    void aNodeComesBackAfterEachMaintenanceStop() {
        // Every 30 days of operation the well stops for 24 hours. Before the fix
        // the stop restarted every hour and the well never came back, so a year
        // produced 30 days' worth.
        Well well = well(NodeStateEnum.RUNNING);
        well.setMaintenanceIntervalInDays(30);
        well.setMaintenanceDuration(24);
        SimWell simWell = new SimWell(well);

        for (int t = 0; t <= 24 * 365; t++) {
            simWell.simulate(t);
        }

        // Cycles of 720 active hours plus a 25-hour stop (the hour that ends it
        // is still a stopped one): 11 full cycles in 8761 hours, then 566 more.
        assertEquals(100f * (11 * 720 + 566), simWell.createResult().getTotalProduced(), 1f);
    }

    @Test
    void aLongerHorizonDeliversMore() {
        Well well = well(NodeStateEnum.RUNNING);
        well.setMaintenanceIntervalInDays(90);
        GatheringNetwork network = gathering();
        InternalConsumption consumption = consumption(1000f);
        Version model = version(List.of(well, network, consumption),
                List.of(connect(well, network), connect(network, consumption)));

        Simulator oneYear = new Simulator(model);
        oneYear.simulate(1, 1);
        Simulator threeYears = new Simulator(model);
        threeYears.simulate(3, 1);

        float first = oneYear.getFinalResult().getMediaOutput();
        float third = threeYears.getFinalResult().getMediaOutput();
        assertTrue(third > 2.9f * first, "three years should deliver about three times one year: " + first + " vs " + third);
    }
}
