package org.enerscope.simulator;

import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.simulator.percentPerNode.PercentGatheringNetwork;
import org.enerscope.simulator.percentPerNode.PercentWell;

import java.util.ArrayList;
import java.util.List;

public class Result {
    private List<PercentWell> percentWells;
    private List<PercentGatheringNetwork> percentGatheringNetworks;

    Result(){
        this.percentWells = new ArrayList<>();
    }

    public void addPercentWell(Well well, Float percent, GatheringNetwork gatheringNetwork){
        percentWells.add(new PercentWell(well, percent, gatheringNetwork));
    }
    public void addPercentGatheringNetwork(Float percent, GatheringNetwork gatheringNetwork, TreatmentPlant treatmentPlant){
        percentGatheringNetworks.add(new PercentGatheringNetwork( percent, gatheringNetwork, treatmentPlant));
    }
}
