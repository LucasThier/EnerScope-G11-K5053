package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.Pipeline;

import java.util.List;
@Getter
@Setter
public class SimPipeline extends SimBaseNode{
    private float maxFlowCapacity;
    private float loss;
    private List<SimBaseNode> nodesBefore;

    SimPipeline(Pipeline pipeline){
        super(pipeline);
        this.maxFlowCapacity = pipeline.getMaxFlowCapacity();
        this.loss = pipeline.getLossPerKm() * pipeline.getLength();
    }

    @Override
    protected void activeAction(int time){
        float capacity = maxFlowCapacity - toDeliver;
        float toGather = (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver()).sum();

        if (toGather >= capacity){
            toDeliver += takeEqualAmounts(nodesBefore,capacity);
        } else {
            toDeliver += calculateAndTakeAll(nodesBefore);
        }

        toDeliver = toDeliver * loss/100;
    }
    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(node -> node.getLastSimulatedTime() == time);
    }
}
