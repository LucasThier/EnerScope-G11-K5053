package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.simulator.ResultPerNode;

import java.util.ArrayList;
import java.util.List;

public class SimPipelineConnection extends SimBaseNode{
    private float transferCapacity;
    private float outputPriority;
    private List<SimBaseNode> nodesBefore;

    public SimPipelineConnection(PipelineConnection pipelineConnection){
        super(pipelineConnection);
        this.transferCapacity = pipelineConnection.getTransferCapacity();
        this.outputPriority = pipelineConnection.getOutputPriority();
        nodesBefore = new ArrayList<>();
        //#############
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        nodesBefore.add(simBaseNode);
    }

    @Override
    protected void activeAction(int time) {
        toDeliver = takeEqualAmounts(nodesBefore, transferCapacity);
        stepOutput = toDeliver.getAmount();
        maxPossibleProduced += transferCapacity;
    }

    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(n -> n.getLastSimulatedTime() == time);
    }

    @Override
    public ResultPerNode createResult() {
        // TODO: Averiguar donde se utiliza esto (2->PipelineConnection)
        return new ResultPerNode(this.id, PipelineConnection.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }
}
