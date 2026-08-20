package org.enerscope.simulator;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.simulator.simNode.*;

import java.util.List;

public class Simulator {
    private List<SimWell> simWells;
    private List<SimGatheringNetwork> simGatheringNetworks;
    private List<SimTreatmentPlant> simTreatmentPlants;
    private List<SimBaseNode> simPipelineAndCompressionPlant;
    private List<SimLiquefactionPlant> simLiquefactionPlants;
    private List<SimSeaportTerminal> simSeaportTerminals;
    private List<SimLNGCarrier> simLNGCarriers;

    Simulator(List<BaseNode> baseNodes, List<NodeConnection> nodeConnections){

    }

    public void simulate(int time){
        int timeInHours = time * 24*365;
        for (int count = 0; count <= timeInHours; count ++){
            int exactTime = count;
            simWells.forEach(simWell -> simWell.simulate(exactTime));
            simGatheringNetworks.forEach(simGatheringNetwork -> simGatheringNetwork.simulate(exactTime));
            simTreatmentPlants.forEach(simTreatmentPlant -> simTreatmentPlant.simulate(exactTime));
            boolean quedanPendientes = true;
            while (quedanPendientes) {
                List<SimBaseNode> readyNodes = simPipelineAndCompressionPlant.stream().filter(simBaseNode -> simBaseNode.readyToBeProcessed(exactTime)).toList();
                readyNodes.forEach(simBaseNode -> simBaseNode.simulate(exactTime));
                quedanPendientes = simPipelineAndCompressionPlant.stream().anyMatch(simBaseNode -> !simBaseNode.readyToBeProcessed(exactTime));
            }
            simLiquefactionPlants.forEach(simLiquefactionPlant -> simLiquefactionPlant.simulate(exactTime));
            simSeaportTerminals.forEach(simSeaportTerminal -> simSeaportTerminal.simulate(exactTime));
            simLNGCarriers.forEach(simLNGCarrier -> simLNGCarrier.simulate(exactTime));
        }
    }
}
