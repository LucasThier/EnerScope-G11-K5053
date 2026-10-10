package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.simulator.auxiliary.ToDeliver;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public abstract class SimBaseLiquefactionPlant extends SimBaseNode{
    protected Float maxProcessingCapacity;
    protected Float MTPARatio;
    protected Float intermediateStorage;
    protected Float gasConsumption;
    protected List<SimBaseNode> nodesBefore;
    protected ToDeliver amountInIntermediateStorage;
    protected float totalDischarged;
    public SimBaseLiquefactionPlant(BaseNode baseModel, Float maxProcessingCapacity, Float MTPARatio, Float intermediateStorage, Float gasConsumption) {
        super(baseModel);
        this.maxProcessingCapacity = maxProcessingCapacity;
        this.MTPARatio = MTPARatio;
        this.intermediateStorage = intermediateStorage;
        this.gasConsumption = gasConsumption;
        this.amountInIntermediateStorage = new ToDeliver(0,0);
        this.nodesBefore = new ArrayList<>();
        this.totalDischarged = 0;
    }

    @Override
    public void activeAction(int time) {
        float amountToTake = (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver().getAmount()).sum();
        ToDeliver toProcess;
        maxPossibleProduced += maxProcessingCapacity;

        if (amountToTake >= maxProcessingCapacity) {
            toProcess = takeEqualAmounts(nodesBefore, maxProcessingCapacity);
        } else {
            toProcess = calculateAndTakeAll(nodesBefore);
        }

        float loss = gasConsumption / 100;
        float lossCase = toProcess.getAmount() * loss;
        toDeliver = new ToDeliver(toProcess.getAmount() - lossCase, toProcess.getContaminant());

        totalDischarged += toDeliver.clean();
        totalDischarged += lossCase;

        toDeliver.setAmount(toDeliver.getAmount() * MTPARatio / 100);

        if((amountInIntermediateStorage.getAmount() + toDeliver.getAmount()) > intermediateStorage){
            amountInIntermediateStorage.setAmount(intermediateStorage);
        } else {
            amountInIntermediateStorage.mix(toDeliver);
        }
    }

    @Override
    public ToDeliver getToDeliver() {
        return amountInIntermediateStorage;
    }

    @Override
    public ToDeliver deliver(float amount) {
        totalDeferred -= amount;
        return amountInIntermediateStorage.deliver(amount);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode) {
        nodesBefore.add(simBaseNode);
    }

    @Override
    public void reset() {
        super.reset();
        this.amountInIntermediateStorage = new ToDeliver(0,0);
        this.totalDischarged = 0;
    }
}
