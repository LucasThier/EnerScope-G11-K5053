package org.enerscope.simulator.percentPerNode;

import lombok.Getter;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;

@Getter
public class PercentGatheringNetwork {
    private TreatmentPlant treatmentPlant;
    private Float percent;
    private GatheringNetwork gatheringNetwork;

    public PercentGatheringNetwork(Float percent, GatheringNetwork gatheringNetwork, TreatmentPlant treatmentPlant){
        this.percent= percent;
        this.gatheringNetwork = gatheringNetwork;
        this.treatmentPlant = treatmentPlant;
    }
}
