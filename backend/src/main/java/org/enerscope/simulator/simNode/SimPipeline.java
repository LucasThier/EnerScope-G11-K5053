package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.simulator.FlowCalculator;

import java.util.List;
@Getter
@Setter
public class SimPipeline extends SimBaseNode{
    private float maxFlowCapacity;
    private float loss;
    private List<SimBaseNode> nodesBefore;

    private float totalGathered;
    private float totalLost;
    private float totalNotDelivered;

    SimPipeline(Pipeline pipeline){
        super(pipeline);
        this.maxFlowCapacity = pipeline.getMaxFlowCapacity();
        this.loss = pipeline.getLossPerKm() * pipeline.getLength();
        this.totalLost = 0;
        this.totalGathered = 0;
        this.totalNotDelivered = 0;
    }

    @Override
    protected void activeAction(int time){
        totalNotDelivered += toDeliver;

        float capacity = maxFlowCapacity - toDeliver;
        float toGather = (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver()).sum();

        if (toGather >= capacity){
            toDeliver += new FlowCalculator().takeEqualAmounts(nodesBefore,capacity,SimBaseNode::getToDeliver,SimBaseNode::deliver);
        } else {
            toDeliver += new FlowCalculator().calculateAndTakeAll(nodesBefore,SimBaseNode::getToDeliver,SimBaseNode::deliver);
        }

        totalGathered += capacity - (maxFlowCapacity - toDeliver);
        totalLost = toDeliver * loss/100;
    }

    @Override
    public boolean readyToBeProcessed() {
        return nodesBefore.stream().anyMatch(simBaseNode -> !simBaseNode.readyToBeProcessed());
    }
}
