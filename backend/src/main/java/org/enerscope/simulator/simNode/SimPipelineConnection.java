package org.enerscope.simulator.simNode;
import lombok.Getter;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.simulator.results.ResultPerNode;

@Getter
public class SimPipelineConnection extends SimBaseNode {
    private float transferCapacity;
    private float outputPriority;
    private SimBaseNode nodeBefore;

    public SimPipelineConnection(PipelineConnection node) {
        super(node);
        transferCapacity = node.getTransferCapacity();
        outputPriority = node.getOutputPriority();
    }

    @Override
    public void addPreviousNode(SimBaseNode node) {
        if (nodeBefore != null)
            throw new IllegalArgumentException("A pipeline connection needs exactly one predecessor");
        nodeBefore = node;
    }

    @Override
    protected void activeAction(int time) {
        if (nodeBefore == null)
            throw new IllegalArgumentException("Pipeline connection has no predecessor");
        float capacity = Math.max(0, transferCapacity - toDeliver.getAmount());
        var received = receive(nodeBefore, Math.min(capacity, nodeBefore.getToDeliver().getAmount()));
        stepOutput = received.getAmount();
        toDeliver.mix(received); maxPossibleProduced += transferCapacity;
    }

    @Override
    public boolean readyToBeProcessed(int time) {
        return nodeBefore != null && nodeBefore.getLastSimulatedTime() == time;
    }

    @Override
    public ResultPerNode createResult() {
        return new ResultPerNode(id, PipelineConnection.class.getSimpleName(), totalProduced, totalDeferred, maxPossibleProduced);
    }
}
