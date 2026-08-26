package org.enerscope.simulator.simNode;

import org.enerscope.node.model.transportation.PipelineConnection;

import java.util.ArrayList;
import java.util.List;

public class SimPipelineConnection extends SimBaseNode{
    private float transferCapacity;
    private float outputPriority;
    private List<SimBaseNode> nodesBefore;

    SimPipelineConnection(PipelineConnection pipelineConnection){
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
}
