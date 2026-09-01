package org.enerscope.simulator;

import lombok.Getter;
import lombok.Setter;
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

    private Result result;

    Simulator(List<BaseNode> baseNodes, List<NodeConnection> nodeConnections){
        simWells = new ArrayList<>();
        simGatheringNetworks = new ArrayList<>();
        simTreatmentPlants = new ArrayList<>();
        simPipelineAndCompressionPlant = new ArrayList<>();
        simLiquefactionPlants = new ArrayList<>();
        simSeaportTerminals = new ArrayList<>();
        simLNGCarriers = new ArrayList<>();

        Map<UUID, SimBaseNode> simNodesById = new HashMap<>();

        baseNodes.forEach(baseNode -> {
            SimBaseNode simNode = transformNode(baseNode);
            if (simNode != null) {
                simNodesById.put(baseNode.getId(), simNode);
            }
        });

        nodeConnections.forEach(connection -> {
            SimBaseNode fromNode = simNodesById.get(connection.getFromNodeId());
            SimBaseNode toNode = simNodesById.get(connection.getToNodeId());

            if (fromNode != null && toNode != null) {
                toNode.addPreviousNode(fromNode);
            }
        });
    }

    public void simulate(int time){
        int timeInHours = time * 24*365;
        for (int count = 0; count <= timeInHours; count ++){
            int exactTime = count;
            simWells.forEach(simWell -> simWell.simulate(exactTime));
            simGatheringNetworks.forEach(simGatheringNetwork -> simGatheringNetwork.simulate(exactTime));
            simTreatmentPlants.forEach(simTreatmentPlant -> simTreatmentPlant.simulate(exactTime));
            boolean quedanPendientes = true;
            List<SimBaseNode> nodesToProcess = new ArrayList<>(simPipelineAndCompressionPlant);

            while (quedanPendientes) {
                List<SimBaseNode> readyNodes = nodesToProcess.stream()
                        .filter(simBaseNode -> simBaseNode.readyToBeProcessed(exactTime))
                        .toList();

                if (readyNodes.isEmpty()) {
                    throw new IllegalStateException("Bloqueo detectado: existen nodos pendientes pero ninguno está listo para procesarse en el tiempo " + exactTime);
                }

                nodesToProcess = nodesToProcess.stream()
                        .filter(simBaseNode -> !simBaseNode.readyToBeProcessed(exactTime))
                        .toList();

                readyNodes.forEach(simBaseNode -> simBaseNode.simulate(exactTime));
                quedanPendientes = !nodesToProcess.isEmpty();
            }
            simLiquefactionPlants.forEach(simLiquefactionPlant -> simLiquefactionPlant.simulate(exactTime));
            simSeaportTerminals.forEach(simSeaportTerminal -> simSeaportTerminal.simulate(exactTime));
            simLNGCarriers.forEach(simLNGCarrier -> simLNGCarrier.simulate(exactTime));
        }
        createResult(time);
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
