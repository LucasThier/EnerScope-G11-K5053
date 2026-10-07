package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.auxiliary.ToDeliver;

import java.util.ArrayList;
import java.util.List;

public class SimInternalConsumption extends SimBaseNode{
    private float consumptionSummer;
    private float consumptionAutumn;
    private float consumptionWinter;
    private float consumptionSpring;
    private List<SimBaseNode> simBaseNodes;

    public SimInternalConsumption(InternalConsumption internalConsumption){
        super(internalConsumption);
        this.consumptionSummer = internalConsumption.getConsumptionSummer();
        this.consumptionAutumn = internalConsumption.getConsumptionAutumn();
        this.consumptionWinter = internalConsumption.getConsumptionWinter();
        this.consumptionSpring = internalConsumption.getConsumptionSpring();
        this.simBaseNodes = new ArrayList<>();
        toDeliver = new ToDeliver(0,0);
    }
    private float getCurrentConsumption(int time) {
        int hourOfYear = time % 8760;

        if (hourOfYear < 2190) {
            return consumptionSummer;
        } else if (hourOfYear < 4380) {
            return consumptionAutumn;
        } else if (hourOfYear < 6570) {
            return consumptionWinter;
        } else {
            return consumptionSpring;
        }
    }

    @Override
    protected void activeAction(int time){
        float currentConsumption = getCurrentConsumption(time);
        float toGather = (float) simBaseNodes.stream().mapToDouble(simWell -> simWell.getToDeliver().getAmount()).sum();

        maxPossibleProduced += currentConsumption;

        if (toGather >= currentConsumption){
            takeEqualAmounts(simBaseNodes,currentConsumption);
        } else {
            calculateAndTakeAll(simBaseNodes);
        }

        totalProduced += Math.min(toGather,currentConsumption);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simBaseNodes.add(simBaseNode);
    }


    @Override
    public ResultPerNode createResult() {
        return new ResultPerNode(this.id, InternalConsumption.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }
}
