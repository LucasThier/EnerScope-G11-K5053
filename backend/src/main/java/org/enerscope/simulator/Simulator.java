package org.enerscope.simulator;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
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
import org.enerscope.version.model.Version;

import java.util.*;

@Getter
@Setter
public class Simulator {
    private UUID versionID;
    private List<SimWell> simWells;
    private List<SimGatheringNetwork> simGatheringNetworks;
    private List<SimTreatmentPlant> simTreatmentPlants;
    private List<SimBaseNode> simPipelineAndCompressionPlant;
    private List<SimLiquefactionPlant> simLiquefactionPlants;
    private List<SimSeaportTerminal> simSeaportTerminals;
    private List<SimLNGCarrier> simLNGCarriers;

    private Version version;
    private Result result;
    private final List<AnnualNodeMetrics> annualMetrics = new ArrayList<>();
    private final Map<UUID, double[]> previousMetrics = new HashMap<>();
    private boolean executed;
    private Integer operatingStartYear;
    private final Map<UUID, BaseNode> physicalNodes = new HashMap<>();

    public Simulator(Version version, int operatingStartYear) {
        this(version);
        this.operatingStartYear = operatingStartYear;
    }


    public Simulator(Version version){
        simWells = new ArrayList<>();
        simGatheringNetworks = new ArrayList<>();
        simTreatmentPlants = new ArrayList<>();
        simPipelineAndCompressionPlant = new ArrayList<>();
        simLiquefactionPlants = new ArrayList<>();
        simSeaportTerminals = new ArrayList<>();
        simLNGCarriers = new ArrayList<>();
        this.version = version;

        Map<UUID, SimBaseNode> simNodesById = new HashMap<>();

        Objects.requireNonNull(version.getNodeSnapshot(), "Node snapshot required").forEach(baseNode -> {
            physicalNodes.put(baseNode.getId(), baseNode);
            SimBaseNode simNode = transformNode(baseNode);
            if (simNode != null) {
                simNodesById.put(baseNode.getId(), simNode);
            }
        });

        version.getConnectionSnapshot().forEach(connection -> {
            SimBaseNode fromNode = simNodesById.get(connection.getFromNodeId());
            SimBaseNode toNode = simNodesById.get(connection.getToNodeId());

            if (fromNode != null && toNode != null) {
                toNode.addPreviousNode(fromNode);
            }
        });
    }

    private void step(SimBaseNode node, int time) {
        if (operatingStartYear != null) {
            node.setCalendarControlled(true);
            BaseNode physical = physicalNodes.get(node.getId());
            java.time.LocalDate start = physical.getStartupDate() == null ? java.time.LocalDate.of(operatingStartYear, 1, 1)
                    : physical.getStartupDate().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            // The existing operational clock uses 365-day years. Keep that convention explicit.
            int startHour = (start.getYear() - operatingStartYear) * 8760 + Math.min(start.getDayOfYear() - 1, 364) * 24;
            int endHour = startHour + Math.toIntExact(Math.round(physical.getLifespanInMonths() * 8760.0 / 12));
            node.setOperatingAgeHours(time - startHour);
            if (!physical.isActive() || time < startHour || time >= endHour) {
                node.setLastSimulatedTime(time);
                return;
            }
        }
        node.simulate(time);
    }

    private void captureYear(int period) {
        List<SimBaseNode> nodes = new ArrayList<>();
        nodes.addAll(simWells); nodes.addAll(simGatheringNetworks); nodes.addAll(simTreatmentPlants);
        nodes.addAll(simPipelineAndCompressionPlant); nodes.addAll(simLiquefactionPlants);
        nodes.addAll(simSeaportTerminals); nodes.addAll(simLNGCarriers);
        for (SimBaseNode n : nodes) {
            double[] current = {n.getMeasuredInput(), n.getMeasuredOutput(), n.getMeasuredExported(),
                    n.getMeasuredLosses(), n.getMeasuredOperatingHours(), n.getMeasuredEvents()};
            double[] previous = previousMetrics.getOrDefault(n.getId(), new double[6]);
            java.math.BigDecimal[] delta = new java.math.BigDecimal[6];
            for (int i = 0; i < 6; i++) {
                if (!Double.isFinite(current[i]) || current[i] < previous[i])
                    throw new IllegalArgumentException("Invalid simulator quantity for node " + n.getId());
                delta[i] = java.math.BigDecimal.valueOf(current[i] - previous[i]);
            }
            annualMetrics.add(new AnnualNodeMetrics(n.getId(), period, delta[0], delta[1], delta[2], delta[3], delta[4], delta[5]));
            previousMetrics.put(n.getId(), current);
        }
    }

    public void simulate(int time){
        if (executed) throw new IllegalStateException("Create a fresh simulator for each evaluation");
        if (time <= 0 || time > 100) throw new IllegalArgumentException("Simulation years must be 1 to 100");
        executed = true;
        int timeInHours = time * 24*365;
        for (int count = 0; count < timeInHours; count ++){
            int exactTime = count;
            simWells.forEach(simWell -> step(simWell, exactTime));
            simGatheringNetworks.forEach(simGatheringNetwork -> step(simGatheringNetwork, exactTime));
            simTreatmentPlants.forEach(simTreatmentPlant -> step(simTreatmentPlant, exactTime));
            boolean quedanPendientes = true;
            List<SimBaseNode> nodesToProcess = new ArrayList<>(simPipelineAndCompressionPlant);

            while (!nodesToProcess.isEmpty()) {
                List<SimBaseNode> readyNodes = nodesToProcess.stream()
                        .filter(simBaseNode -> simBaseNode.readyToBeProcessed(exactTime))
                        .toList();

                if (readyNodes.isEmpty()) {
                    throw new IllegalStateException("Bloqueo detectado: existen nodos pendientes pero ninguno está listo para procesarse en el tiempo " + exactTime);
                }

                nodesToProcess = nodesToProcess.stream()
                        .filter(simBaseNode -> !simBaseNode.readyToBeProcessed(exactTime))
                        .toList();

                readyNodes.forEach(simBaseNode -> step(simBaseNode, exactTime));
                quedanPendientes = !nodesToProcess.isEmpty();
            }
            simLiquefactionPlants.forEach(simLiquefactionPlant -> step(simLiquefactionPlant, exactTime));
            simSeaportTerminals.forEach(simSeaportTerminal -> step(simSeaportTerminal, exactTime));
            simLNGCarriers.forEach(simLNGCarrier -> step(simLNGCarrier, exactTime));
            if ((count + 1) % 8760 == 0) captureYear((count + 1) / 8760);
        }
        createResult(time);

        version.addResult(result);
    }

    private void createResult(int time) {
        Result result = new Result(time);
        result.addAllResultPerNodes(simWells.stream().map(SimWell::createResult).toList());
        result.addAllResultPerNodes(simGatheringNetworks.stream().map(SimGatheringNetwork::createResult).toList());
        result.addAllResultPerNodes(simTreatmentPlants.stream().map(SimTreatmentPlant::createResult).toList());
        result.addAllResultPerNodes(simPipelineAndCompressionPlant.stream().map(simBaseNode -> simBaseNode.createResult()).toList());
        result.addAllResultPerNodes(simLiquefactionPlants.stream().map(SimLiquefactionPlant::createResult).toList());
        result.addAllResultPerNodes(simSeaportTerminals.stream().map(SimSeaportTerminal::createResult).toList());
        result.addAllResultPerNodes(simLNGCarriers.stream().map(SimLNGCarrier::createResult).toList());
        this.result = result;
    }

    private SimBaseNode transformNode(BaseNode baseNode) {
        SimBaseNode simNode = null;

        if (baseNode instanceof Well well) {
            SimWell simWell = new SimWell(well);
            simWells.add(simWell);
            simNode = simWell;
        } else if (baseNode instanceof GatheringNetwork gatheringNetwork) {
            SimGatheringNetwork simGatheringNetwork = new SimGatheringNetwork(gatheringNetwork);
            simGatheringNetworks.add(simGatheringNetwork);
            simNode = simGatheringNetwork;
        } else if (baseNode instanceof TreatmentPlant treatmentPlants) {
            SimTreatmentPlant simTreatmentPlant = new SimTreatmentPlant(treatmentPlants);
            simTreatmentPlants.add(simTreatmentPlant);
            simNode = simTreatmentPlant;
        } else if (baseNode instanceof Pipeline pipeline) {
            SimPipeline simPipeline = new SimPipeline(pipeline);
            simPipelineAndCompressionPlant.add(simPipeline);
            simNode = simPipeline;
        } else if (baseNode instanceof org.enerscope.node.model.transportation.PipelineConnection connection) {
            SimPipelineConnection simConnection = new SimPipelineConnection(connection);
            simPipelineAndCompressionPlant.add(simConnection);
            simNode = simConnection;
        } else if (baseNode instanceof CompressingPlant compressingPlant) {
            SimCompressingPlant simCompressingPlant = new SimCompressingPlant(compressingPlant);
            simPipelineAndCompressionPlant.add(simCompressingPlant);
            simNode = simCompressingPlant;
        } else if (baseNode instanceof GroundBasedLiquefactionPlant groundBasedLiquefactionPlant) {
            SimLiquefactionPlant simLiquefactionPlant = new SimLiquefactionPlant(groundBasedLiquefactionPlant);
            simLiquefactionPlants.add(simLiquefactionPlant);
            simNode = simLiquefactionPlant;
        } else if (baseNode instanceof FLNGUnit flngUnit) {
            SimLiquefactionPlant simLiquefactionPlant = new SimLiquefactionPlant(flngUnit);
            simLiquefactionPlants.add(simLiquefactionPlant);
            simNode = simLiquefactionPlant;
        } else if (baseNode instanceof SeaportTerminal seaportTerminal) {
            SimSeaportTerminal simSeaportTerminal = new SimSeaportTerminal(seaportTerminal);
            simSeaportTerminals.add(simSeaportTerminal);
            simNode = simSeaportTerminal;
        } else if (baseNode instanceof LNGCarrier lngCarrier) {
            SimLNGCarrier simLNGCarrier = new SimLNGCarrier(lngCarrier);
            simLNGCarriers.add(simLNGCarrier);
            simNode = simLNGCarrier;
        }

        return simNode;
    }

}
