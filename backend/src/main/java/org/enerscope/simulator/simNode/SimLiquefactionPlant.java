package org.enerscope.simulator.simNode;

import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;

import java.util.List;

public class SimLiquefactionPlant extends SimBaseNode{
    private Float maxProcessingCapacity;
    private Float MTPARatio;
    private Float intermediateStorage;
    private Float gasConsumption;

    private List<SimBaseNode> nodesBefore;

    private float totalProcesed;
    private float totalDelivered;
    private float totalLost;
    private float amountInIntermediateStorage;

    SimLiquefactionPlant(GroundBasedLiquefactionPlant groundBasedLiquefactionPlant){
        super(groundBasedLiquefactionPlant);
        this.maxProcessingCapacity = groundBasedLiquefactionPlant.getMaxProcessingCapacity();
        this.MTPARatio = groundBasedLiquefactionPlant.getMTPARatio();
        this.intermediateStorage = groundBasedLiquefactionPlant.getIntermediateStorage();
        this.gasConsumption = groundBasedLiquefactionPlant.getGasConsumption();
        this.amountInIntermediateStorage = 0;
    }
    SimLiquefactionPlant(FLNGUnit flngUnit){
        super(flngUnit);
        this.maxProcessingCapacity = flngUnit.getMaxProcessingCapacity();
        this.MTPARatio = flngUnit.getMTPARatio();
        this.intermediateStorage = flngUnit.getIntermediateStorage();
        this.gasConsumption = flngUnit.getGasConsumption();
        this.amountInIntermediateStorage = 0;
    }

    @Override
    protected void activeAction(int time){

        float amountToTake =  (float) nodesBefore.stream().mapToDouble(SimBaseNode::getToDeliver).sum();
        float toProccess;

        if(amountToTake >= maxProcessingCapacity){
            toProccess = takeEqualAmounts(nodesBefore,maxProcessingCapacity);
        } else {
            toProccess = calculateAndTakeAll(nodesBefore);
        }

        float loss =  gasConsumption /100;
        float lossCase;

        if(toProccess >= maxProcessingCapacity){
            lossCase = maxProcessingCapacity * loss;
            toDeliver = maxProcessingCapacity -= lossCase;
        } else {
            lossCase = toProccess * loss;
            toDeliver = toProccess - lossCase;
        }

        toDeliver = toDeliver * MTPARatio /100;

        totalLost += lossCase;
        totalProcesed += toDeliver;

        if((amountInIntermediateStorage + toDeliver) > intermediateStorage){
            totalLost = amountInIntermediateStorage + toDeliver - intermediateStorage;
            amountInIntermediateStorage = intermediateStorage;
        } else {
            amountInIntermediateStorage = toDeliver;
        }
    }

    @Override
    public float getToDeliver() {
        return amountInIntermediateStorage;
    }

    @Override
    public void deliver(float amount){
        amountInIntermediateStorage -= amount;
        totalDelivered += amount;
    }
}
