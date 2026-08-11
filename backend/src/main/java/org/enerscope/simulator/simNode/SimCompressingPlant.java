package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.simulator.FlowCalculator;

import java.util.List;

@Getter
@Setter
public class SimCompressingPlant extends SimBaseNode{
    private float maxCompressionCapacity;
    private float processWaste;
    private float gasConsumption;

    private List<SimBaseNode> nodesBefore;
    private float totalProcesed;
    private float totalDelivered;
    SimCompressingPlant(CompressingPlant compressingPlant){
        super(compressingPlant);
        this.maxCompressionCapacity = compressingPlant.getMaxCompressionCapacity();
        this.processWaste = compressingPlant.getProcessWaste();
        this.gasConsumption = compressingPlant.getGasConsumption();
        this.totalProcesed = 0;
        this.totalDelivered = 0;
    }
    @Override
    protected void activeAction(int time){
        totalDelivered -= toDeliver;

        float amountToTake =  (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver()).sum();
        float toProccess;

        if(amountToTake >= maxCompressionCapacity){
            toProccess = new FlowCalculator().takeEqualAmounts(nodesBefore,maxCompressionCapacity,SimBaseNode::getToDeliver,SimBaseNode::deliver);
        } else {
            toProccess = new FlowCalculator().calculateAndTakeAll(nodesBefore,SimBaseNode::getToDeliver,SimBaseNode::deliver);
        }
        float loss = ( processWaste + gasConsumption ) /100;
        if(toProccess >= maxCompressionCapacity){
            toDeliver = maxCompressionCapacity * loss;
        } else {
            toDeliver = toProccess * loss;
        }
        totalProcesed += toDeliver;
        totalDelivered += toDeliver;
    }
}
