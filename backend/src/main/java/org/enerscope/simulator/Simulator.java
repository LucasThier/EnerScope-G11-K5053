package org.enerscope.simulator;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.node.model.export.InternalConsumption;
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
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.ResultPerRound;
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
    private List<SimBaseNode> simPipelineAndCompressionPlantAndPipelineConnection;
    private List<SimBaseLiquefactionPlant> simBaseLiquefactionPlants;
    private List<SimSeaportTerminal> simSeaportTerminals;
    private List<SimLNGCarrier> simLNGCarriers;
    private List<SimBaseNode> consumptionNodes;

    private Version version;
    private List<ResultPerRound> resultsPerRound;
    private FinalResult finalResult;



    Simulator(Version version){
        simWells = new ArrayList<>();
        simGatheringNetworks = new ArrayList<>();
        simTreatmentPlants = new ArrayList<>();
        simPipelineAndCompressionPlantAndPipelineConnection = new ArrayList<>();
        simBaseLiquefactionPlants = new ArrayList<>();
        simSeaportTerminals = new ArrayList<>();
        simLNGCarriers = new ArrayList<>();
        consumptionNodes = new ArrayList<>();
        this.version = version;
        resultsPerRound = new ArrayList<>();

        Map<UUID, SimBaseNode> simNodesById = new HashMap<>();

        version.getNodeSnapshot().forEach(baseNode -> {
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

    public void simulate(int time){
        int timeInHours = time * 24*365;
        for(int amount = 0; amount < 10; amount ++){
            for (int count = 0; count <= timeInHours; count ++){
                int exactTime = count;
                simWells.forEach(simWell -> simWell.simulate(exactTime));
                simGatheringNetworks.forEach(simGatheringNetwork -> simGatheringNetwork.simulate(exactTime));
                simTreatmentPlants.forEach(simTreatmentPlant -> simTreatmentPlant.simulate(exactTime));
                boolean quedanPendientes = true;
                List<SimBaseNode> nodesToProcess = new ArrayList<>(simPipelineAndCompressionPlantAndPipelineConnection);

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
                consumptionNodes.forEach(simLiquefactionPlant -> simLiquefactionPlant.simulate(exactTime));
                simBaseLiquefactionPlants.forEach(simGroundBasedLiquefactionPlant -> simGroundBasedLiquefactionPlant.simulate(exactTime));
                simSeaportTerminals.forEach(simSeaportTerminal -> simSeaportTerminal.simulate(exactTime));
                simLNGCarriers.forEach(simLNGCarrier -> simLNGCarrier.simulate(exactTime));
            }
            createResultPerRound();
            resetAll();
        }
        orderResults();
        createFinalResult(time);

        version.addResult(finalResult);
    }

    private void resetAll() {
        simWells.forEach(node -> node.reset());
        simGatheringNetworks.forEach(node -> node.reset());
        simTreatmentPlants.forEach(node -> node.reset());
        simPipelineAndCompressionPlantAndPipelineConnection.forEach(node -> node.reset());
        simBaseLiquefactionPlants.forEach(node -> node.reset());
        simSeaportTerminals.forEach(node -> node.reset());
        simLNGCarriers.forEach(node -> node.reset());
        consumptionNodes.forEach(node -> node.reset());
    }

    private void orderResults() {
        List<ResultPerRound> resultsPerRoundOrder = this.resultsPerRound.stream().sorted(Comparator.comparingDouble(ResultPerRound::amountProduced)).toList();
        this.resultsPerRound = resultsPerRoundOrder;
    }

    private void createResultPerRound() {
        ResultPerRound resultPerRound = new ResultPerRound();
        resultPerRound.addAllResultPerNodes(simWells.stream().map(SimWell::createResult).toList());
        resultPerRound.addAllResultPerNodes(simGatheringNetworks.stream().map(SimGatheringNetwork::createResult).toList());
        resultPerRound.addAllResultPerNodes(simTreatmentPlants.stream().map(SimTreatmentPlant::createResult).toList());
        resultPerRound.addAllResultPerNodes(simPipelineAndCompressionPlantAndPipelineConnection.stream().map(simBaseNode -> simBaseNode.createResult()).toList());
        resultPerRound.addAllResultPerNodes(simBaseLiquefactionPlants.stream().map(SimBaseLiquefactionPlant::createResult).toList());
        resultPerRound.addAllResultPerNodes(simSeaportTerminals.stream().map(SimSeaportTerminal::createResult).toList());
        resultPerRound.addAllResultPerNodes(simLNGCarriers.stream().map(SimLNGCarrier::createResult).toList());
        resultPerRound.addAllResultPerNodes(consumptionNodes.stream().map(SimBaseNode::createResult).toList());

        this.resultsPerRound.add(resultPerRound);
    }

    private void createFinalResult(int time) {
        FinalResult finalResult = new FinalResult(time);
        finalResult.setMediaOutput(mediaOutput());
        finalResult.setPercentile90(getPercentile(90).getResultPerNodes());
        finalResult.setPercentile50(getPercentile(50).getResultPerNodes());
        finalResult.setPercentile10(getPercentile(10).getResultPerNodes());
        this.finalResult = finalResult;
    }

    private float mediaOutput(){
        int size = this.resultsPerRound.size();
        float sum = (float) this.resultsPerRound.stream().mapToDouble(value -> value.amountProduced()).sum();
        return sum / size;
    }

    private ResultPerRound getPercentile(double percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * resultsPerRound.size()) - 1;
        if (index < 0) index = 0;
        if (index >= resultsPerRound.size()) index = resultsPerRound.size() - 1;
        return resultsPerRound.get(index);
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
            simPipelineAndCompressionPlantAndPipelineConnection.add(simPipeline);
            simNode = simPipeline;
        } else if (baseNode instanceof PipelineConnection pipelineConnection) {
            SimPipelineConnection simPipelineConnection = new SimPipelineConnection(pipelineConnection);
            simPipelineAndCompressionPlantAndPipelineConnection.add(simPipelineConnection);
            simNode = simPipelineConnection;
        } else if (baseNode instanceof CompressingPlant compressingPlant) {
            SimCompressingPlant simCompressingPlant = new SimCompressingPlant(compressingPlant);
            simPipelineAndCompressionPlantAndPipelineConnection.add(simCompressingPlant);
            simNode = simCompressingPlant;
        } else if (baseNode instanceof GroundBasedLiquefactionPlant groundBasedLiquefactionPlant) {
            SimGroundBasedLiquefactionPlant simGroundBasedLiquefactionPlant = new SimGroundBasedLiquefactionPlant(groundBasedLiquefactionPlant);
            simBaseLiquefactionPlants.add(simGroundBasedLiquefactionPlant);
            simNode = simGroundBasedLiquefactionPlant;
        } else if (baseNode instanceof FLNGUnit flngUnit) {
            SimFLNGUnit simFLNGUnit = new SimFLNGUnit(flngUnit);
            simBaseLiquefactionPlants.add(simFLNGUnit);
            simNode = simFLNGUnit;
        } else if (baseNode instanceof SeaportTerminal seaportTerminal) {
            SimSeaportTerminal simSeaportTerminal = new SimSeaportTerminal(seaportTerminal);
            simSeaportTerminals.add(simSeaportTerminal);
            simNode = simSeaportTerminal;
        } else if (baseNode instanceof LNGCarrier lngCarrier) {
            SimLNGCarrier simLNGCarrier = new SimLNGCarrier(lngCarrier);
            simLNGCarriers.add(simLNGCarrier);
            simNode = simLNGCarrier;
        } else if (baseNode instanceof InternalConsumption internalConsumption) {
            SimInternalConsumption simInternalConsumption = new SimInternalConsumption(internalConsumption);
            consumptionNodes.add(simInternalConsumption);
            simNode = simInternalConsumption;
        } else if (baseNode instanceof IndustrialConsumption industrialConsumption) {
            SimIndustrialConsumption simIndustrialConsumption = new SimIndustrialConsumption(industrialConsumption);
            consumptionNodes.add(simIndustrialConsumption);
            simNode = simIndustrialConsumption;
        }

        return simNode;
    }

}


