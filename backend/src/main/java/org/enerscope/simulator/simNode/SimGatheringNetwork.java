package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.simulator.FlowCalculator;

import java.util.List;

@Getter
@Setter
public class SimGatheringNetwork extends SimBaseNode{
    private float maxTransportCapacity;
    private float loss;
    private List<SimWell> simWells;

    private float totalGathered;
    private float totalLost;
    private float totalNotDelivered;
    SimGatheringNetwork(GatheringNetwork gatheringNetwork){
        super(gatheringNetwork);
        this.maxTransportCapacity = gatheringNetwork.getMaxTransportCapacity();
        this.loss = gatheringNetwork.getLength() * gatheringNetwork.getLossPerMeter();
        this.totalLost = 0;
        this.totalGathered = 0;
        this.totalNotDelivered = 0;
    }

    @Override
    protected void activeAction(int time){
        totalNotDelivered += toDeliver;

        float capacity = maxTransportCapacity - toDeliver;
        float toGather = (float) simWells.stream().mapToDouble(simWell -> simWell.getToDeliver()).sum();

        if (toGather >= capacity){
            toDeliver += new FlowCalculator().takeEqualAmounts(simWells,capacity,SimWell::getToDeliver,SimWell::deliver);
        } else {
            toDeliver += new FlowCalculator().calculateAndTakeAll(simWells,SimWell::getToDeliver,SimWell::deliver);
        }

        totalGathered += capacity - (maxTransportCapacity - toDeliver);
        totalLost = toDeliver * loss/100;
    }
}
