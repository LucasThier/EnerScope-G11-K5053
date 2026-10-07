package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.auxiliary.ToDeliver;

import java.util.ArrayList;
import java.util.List;

public class SimIndustrialConsumption extends SimBaseNode{
    private float consumption;
    private List<SimBaseNode> simBaseNodes;

    public SimIndustrialConsumption(IndustrialConsumption industrialConsumption){
        super(industrialConsumption);
        this.consumption = industrialConsumption.getConsumption();
        this.simBaseNodes = new ArrayList<>();
        toDeliver = new ToDeliver(0,0);
    }

    @Override
    protected void activeAction(int time){
        float toGather = (float) simBaseNodes.stream().mapToDouble(simWell -> simWell.getToDeliver().getAmount()).sum();

        maxPossibleProduced += consumption;

        if (toGather >= consumption){
            takeEqualAmounts(simBaseNodes,consumption);
        } else {
            calculateAndTakeAll(simBaseNodes);
        }

        totalProduced += Math.min(toGather,consumption);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simBaseNodes.add(simBaseNode);
    }


    @Override
    public ResultPerNode createResult() {
        return new ResultPerNode(this.id, IndustrialConsumption.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }
}
