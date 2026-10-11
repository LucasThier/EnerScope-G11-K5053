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
    private List<SimPipeline> simPipelines;
    private List<SimPipelineConnection> simPipelineConnections;
    private List<SimCompressingPlant> simCompressingPlants;
    private List<SimGroundBasedLiquefactionPlant> simGroundBasedLiquefactionPlants;
    private List<SimFLNGUnit> simFLNGUnits;
    private List<SimSeaportTerminal> simSeaportTerminals;
    private List<SimLNGCarrier> simLNGCarriers;
    private List<SimIndustrialConsumption> simIndustrialConsumptions;
    private List<SimInternalConsumption> simInternalConsumptions;

    private List<SimBaseNode> executionOrder;

    private Version version;
    private List<ResultPerRound> resultsPerRound;
    private FinalResult finalResult;



    Simulator(Version version){
        simWells = new ArrayList<>();
        simGatheringNetworks = new ArrayList<>();
        simTreatmentPlants = new ArrayList<>();
        simPipelines = new ArrayList<>();
        simPipelineConnections = new ArrayList<>();
        simCompressingPlants = new ArrayList<>();
        simGroundBasedLiquefactionPlants = new ArrayList<>();
        simFLNGUnits = new ArrayList<>();
        simSeaportTerminals = new ArrayList<>();
        simLNGCarriers = new ArrayList<>();
        simIndustrialConsumptions = new ArrayList<>();
        simInternalConsumptions = new ArrayList<>();

        executionOrder = new ArrayList<>();

        this.version = version;
        resultsPerRound = new ArrayList<>();

        Map<UUID, SimBaseNode> simNodesById = new HashMap<>();
        List<SimBaseNode> allNodes = new ArrayList<>();

        version.getNodeSnapshot().forEach(baseNode -> {
            SimBaseNode simNode = transformNode(baseNode);
            if (simNode != null) {
                simNodesById.put(baseNode.getId(), simNode);
                allNodes.add(simNode);
            }
        });

        Map<SimBaseNode, List<SimBaseNode>> successors = new HashMap<>();
        Map<SimBaseNode, Integer> inDegree = new HashMap<>();

        allNodes.forEach(node -> {
            successors.put(node, new ArrayList<>());
            inDegree.put(node, 0);
        });

        version.getConnectionSnapshot().forEach(connection -> {
            SimBaseNode fromNode = simNodesById.get(connection.getFromNodeId());
            SimBaseNode toNode = simNodesById.get(connection.getToNodeId());

            if (fromNode != null && toNode != null) {
                successors.get(fromNode).add(toNode);
                inDegree.put(toNode, inDegree.get(toNode) + 1);
                toNode.addPreviousNode(fromNode);
            }
        });

        this.executionOrder = computeTopologicalOrderWithPriority(allNodes, successors, inDegree);
    }
    private List<SimBaseNode> computeTopologicalOrderWithPriority(List<SimBaseNode> allNodes, Map<SimBaseNode, List<SimBaseNode>> successors, Map<SimBaseNode, Integer> inDegree) {

        Queue<SimBaseNode> queue = new LinkedList<>();

        allNodes.stream().filter(node -> inDegree.get(node) == 0).forEach(queue::add);

        List<SimBaseNode> sortedList = new ArrayList<>();

        while (!queue.isEmpty()) {
            SimBaseNode current = queue.poll();
            sortedList.add(current);

            List<SimBaseNode> nextNodes = new ArrayList<>(successors.get(current));

            nextNodes.sort((n1, n2) -> {
                if (n1 instanceof SimPipelineConnection p1 && n2 instanceof SimPipelineConnection p2) {
                    return Float.compare(p1.getOutputPriority(), p2.getOutputPriority());
                }
                return 0;
            });

            for (SimBaseNode neighbor : nextNodes) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        return sortedList;
    }
    public void simulate(int time){
        int timeInHours = time * 24*365;
        for(int amount = 0; amount < 10; amount ++){
            for (int count = 0; count <= timeInHours; count ++){
                int exactTime = count;
                executionOrder.forEach(node -> node.simulate(exactTime));
            }
            ResultPerRound resultPerRound = new ResultPerRound();
            resultPerRound.addAllResultPerNodes(executionOrder.stream().map(SimBaseNode::createResult).toList());
            resultsPerRound.add(resultPerRound);
            executionOrder.forEach(node -> node.reset());
        }
        orderResults();
        createFinalResult(time);

        version.addResult(finalResult);
    }

    private void orderResults() {
        List<ResultPerRound> resultsPerRoundOrder = this.resultsPerRound.stream().sorted(Comparator.comparingDouble(ResultPerRound::amountProduced)).toList();
        this.resultsPerRound = resultsPerRoundOrder;
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
            simPipelines.add(simPipeline);
            simNode = simPipeline;
        } else if (baseNode instanceof PipelineConnection pipelineConnection) {
            SimPipelineConnection simPipelineConnection = new SimPipelineConnection(pipelineConnection);
            simPipelineConnections.add(simPipelineConnection);
            simNode = simPipelineConnection;
        } else if (baseNode instanceof CompressingPlant compressingPlant) {
            SimCompressingPlant simCompressingPlant = new SimCompressingPlant(compressingPlant);
            simCompressingPlants.add(simCompressingPlant);
            simNode = simCompressingPlant;
        } else if (baseNode instanceof GroundBasedLiquefactionPlant groundBasedLiquefactionPlant) {
            SimGroundBasedLiquefactionPlant simGroundBasedLiquefactionPlant = new SimGroundBasedLiquefactionPlant(groundBasedLiquefactionPlant);
            simGroundBasedLiquefactionPlants.add(simGroundBasedLiquefactionPlant);
            simNode = simGroundBasedLiquefactionPlant;
        } else if (baseNode instanceof FLNGUnit flngUnit) {
            SimFLNGUnit simFLNGUnit = new SimFLNGUnit(flngUnit);
            simFLNGUnits.add(simFLNGUnit);
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
            simInternalConsumptions.add(simInternalConsumption);
            simNode = simInternalConsumption;
        } else if (baseNode instanceof IndustrialConsumption industrialConsumption) {
            SimIndustrialConsumption simIndustrialConsumption = new SimIndustrialConsumption(industrialConsumption);
            simIndustrialConsumptions.add(simIndustrialConsumption);
            simNode = simIndustrialConsumption;
        }

        return simNode;
    }

}


