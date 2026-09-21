package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.simulator.ResultPerNode;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimGatheringNetwork extends SimBaseNode{
    private float maxTransportCapacity;
    private float loss;
    private List<SimWell> simWells;
    private float totalLoss;

    public SimGatheringNetwork(GatheringNetwork gatheringNetwork){
        super(gatheringNetwork);
        this.maxTransportCapacity = gatheringNetwork.getMaxTransportCapacity();
        this.loss = gatheringNetwork.getLength() * gatheringNetwork.getLossPerMeter();
        simWells = new ArrayList<>();
        totalLoss = 0;
    }

    @Override
    protected void activeAction(int time){
        float capacity = maxTransportCapacity - toDeliver.getAmount();
        float toGather = (float) simWells.stream().mapToDouble(simWell -> simWell.getToDeliver().getAmount()).sum();

        maxPossibleProduced += maxTransportCapacity;

        if (toGather >= capacity){
            toDeliver.mix(takeEqualAmounts(simWells,capacity));
        } else {
            toDeliver.mix(calculateAndTakeAll(simWells));
        }

        float lost = toDeliver.getAmount() * loss /100;
        totalLoss += lost;
        toDeliver.setAmount(toDeliver.getAmount() - lost);

    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simWells.add((SimWell) simBaseNode);
    }

    @Override
    public ResultPerNode createResult() {
        ResultPerNode resultPerNode = new ResultPerNode(this.id, GatheringNetwork.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
        resultPerNode.setExtra(totalLoss);
        return resultPerNode;
    }

    @Override
    public void reset() {
        super.reset();
        totalLoss = 0;
    }
}
