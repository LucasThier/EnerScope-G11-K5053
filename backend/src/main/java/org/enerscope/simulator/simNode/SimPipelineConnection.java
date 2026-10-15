package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.simulator.auxiliary.ToDeliver;
import org.enerscope.simulator.results.ResultPerNode;


@Getter
@Setter
public class SimPipelineConnection extends SimBaseNode{
    private float transferCapacity;
    private float outputPriority;
    private SimBaseNode nodeBefore;

    public SimPipelineConnection(PipelineConnection pipelineConnection){
        super(pipelineConnection);
        this.transferCapacity = pipelineConnection.getTransferCapacity();
        this.outputPriority = pipelineConnection.getOutputPriority();
    }

    @Override
    protected void activeAction(int time){
        if (nodeBefore == null) {
            return;
        }
        ToDeliver totalArrived = nodeBefore.getToDeliver();

        float capacity = Math.min(transferCapacity, transferCapacity - toDeliver.getAmount());
        float toGather = totalArrived.getAmount();
        maxPossibleProduced += transferCapacity;

        if (toGather >= capacity){
            toDeliver.mix(totalArrived.deliver(capacity));
        } else {
            toDeliver.mix(totalArrived.deliver(toGather));
        }

    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        nodeBefore = simBaseNode;
    }

    @Override
    public ResultPerNode createResult() {
        return new ResultPerNode(this.id, PipelineConnection.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }

    @Override
    public boolean readyToBeProcessed(int time) {
        return nodeBefore != null && nodeBefore.getLastSimulatedTime() == time;
    }
}
