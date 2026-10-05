package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.simulator.ResultPerNode;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
public class SimPipeline extends SimBaseNode{
    private float maxFlowCapacity;
    private float loss;
    private List<SimBaseNode> nodesBefore;
    private float totalLost;

    public SimPipeline(Pipeline pipeline){
        super(pipeline);
        this.maxFlowCapacity = pipeline.getMaxFlowCapacity();
        this.loss = pipeline.getLossPerKm() * pipeline.getLength();
        nodesBefore = new ArrayList<>();
        totalLost = 0;
    }

    @Override
    protected void activeAction(int time){
        float capacity = maxFlowCapacity - toDeliver.getAmount();
        float toGather = (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver().getAmount()).sum();
        maxPossibleProduced += maxFlowCapacity;

        if (toGather >= capacity){
            toDeliver.mix(takeEqualAmounts(nodesBefore,capacity));
        } else {
            toDeliver.mix(calculateAndTakeAll(nodesBefore));
        }
        float lost = toDeliver.getAmount() * (loss/100);
        totalLost += lost;
        toDeliver.setAmount(toDeliver.getAmount() - lost);
    }
    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(node -> node.getLastSimulatedTime() == time);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        nodesBefore.add(simBaseNode);
    }

    @Override
    public ResultPerNode createResult() {
        ResultPerNode result = new ResultPerNode(this.id, Pipeline.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
        result.setExtra(totalLost);
        return result;
    }
}
