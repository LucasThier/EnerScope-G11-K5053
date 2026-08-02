package org.enerscope.simulator.percentPerNode;

import lombok.Getter;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.Well;

@Getter
public class PercentWell {
    private Well well;
    private Float percent;
    private GatheringNetwork gatheringNetwork;

    public PercentWell(Well well, Float percent, GatheringNetwork gatheringNetwork){
        this.percent= percent;
        this.well = well;
        this.gatheringNetwork = gatheringNetwork;
    }
}
