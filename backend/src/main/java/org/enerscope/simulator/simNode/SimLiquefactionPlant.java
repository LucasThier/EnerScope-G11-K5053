package org.enerscope.simulator.simNode;

import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.simulator.ResultPerNode;
import org.enerscope.simulator.ToDeliver;

import java.util.ArrayList;
import java.util.List;

public class SimLiquefactionPlant extends SimBaseNode{
    private Float maxProcessingCapacity;
    private Float MTPARatio;
    private Float intermediateStorage;
    private Float gasConsumption;
    private List<SimBaseNode> nodesBefore;
    private ToDeliver amountInIntermediateStorage;
    private float totalDischarged;
    private String nodeClass;

    public SimLiquefactionPlant(GroundBasedLiquefactionPlant groundBasedLiquefactionPlant){
        super(groundBasedLiquefactionPlant);
        this.maxProcessingCapacity = groundBasedLiquefactionPlant.getMaxProcessingCapacity();
        this.MTPARatio = groundBasedLiquefactionPlant.getMTPARatio();
        this.intermediateStorage = groundBasedLiquefactionPlant.getIntermediateStorage();
        this.gasConsumption = groundBasedLiquefactionPlant.getGasConsumption();
        this.amountInIntermediateStorage = new ToDeliver(0,0);
        nodesBefore = new ArrayList<>();
        totalDischarged = 0;
        nodeClass = GroundBasedLiquefactionPlant.class.getSimpleName();
    }
    public SimLiquefactionPlant(FLNGUnit flngUnit){
        super(flngUnit);
        this.maxProcessingCapacity = flngUnit.getMaxProcessingCapacity();
        this.MTPARatio = flngUnit.getMTPARatio();
        this.intermediateStorage = flngUnit.getIntermediateStorage();
        this.gasConsumption = flngUnit.getGasConsumption();
        this.amountInIntermediateStorage = new ToDeliver(0,0);
        nodesBefore = new ArrayList<>();
        totalDischarged = 0;
        nodeClass = FLNGUnit.class.getSimpleName();
    }

    @Override
    protected void activeAction(int time){
        float amountToTake =  (float) nodesBefore.stream().mapToDouble(simBaseNode ->simBaseNode.getToDeliver().getAmount() ).sum();
        ToDeliver toProcess;
        maxPossibleProduced += maxProcessingCapacity;

        if(amountToTake >= maxProcessingCapacity){
            toProcess = takeEqualAmounts(nodesBefore,maxProcessingCapacity);
        } else {
            toProcess = calculateAndTakeAll(nodesBefore);
        }

        float loss =  gasConsumption /100;
        float lossCase;

        if(toProcess.getAmount() >= maxProcessingCapacity){
            lossCase = maxProcessingCapacity * loss;
            toDeliver = new ToDeliver(maxProcessingCapacity - lossCase,0);
        } else {
            lossCase = toProcess.getAmount() * loss;
            toDeliver = new ToDeliver(toProcess.getAmount() - lossCase,0);
        }

        totalDischarged = toDeliver.clean();
        toDeliver.setAmount(toDeliver.getAmount() * MTPARatio /100);

        if((amountInIntermediateStorage.getAmount() + toDeliver.getAmount()) > intermediateStorage){
            amountInIntermediateStorage.setAmount(intermediateStorage);
        } else {
            amountInIntermediateStorage = toDeliver;
        }
    }

    @Override
    public ToDeliver getToDeliver() {
        return amountInIntermediateStorage;
    }

    @Override
    public ToDeliver deliver(float amount){
        totalDeferred -= amount;
        return amountInIntermediateStorage.deliver(amount);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        nodesBefore.add(simBaseNode);
    }

    @Override
    public ResultPerNode createResult() {
        ResultPerNode resultPerNode = new ResultPerNode(this.id, nodeClass,totalProduced,totalDeferred,maxPossibleProduced);
        resultPerNode.setExtra(totalDischarged);
        return resultPerNode;
    }
}
