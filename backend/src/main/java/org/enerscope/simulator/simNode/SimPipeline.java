package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.Pipeline;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
public class SimPipeline extends SimBaseNode{
    private float maxFlowCapacity;
    private float loss;
    private List<SimBaseNode> nodesBefore;

    public SimPipeline(Pipeline pipeline){
        super(pipeline);
        this.maxFlowCapacity = pipeline.getMaxFlowCapacity();
        this.loss = pipeline.getLossPerKm() * pipeline.getLength();
        nodesBefore = new ArrayList<>();
    }

    @Override
    protected void activeAction(int time){
        float capacity = maxFlowCapacity - toDeliver.getAmount();
        float toGather = (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver().getAmount()).sum();

        if (toGather >= capacity){
            toDeliver.mix(takeEqualAmounts(nodesBefore,capacity));
        } else {
            toDeliver.mix(calculateAndTakeAll(nodesBefore));
        }

        toDeliver.setAmount(toDeliver.getAmount() * (1 - loss/100));
    }
    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(node -> node.getLastSimulatedTime() == time);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        nodesBefore.add(simBaseNode);
    }
}
