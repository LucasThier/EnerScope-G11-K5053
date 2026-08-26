package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.CompressingPlant;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimCompressingPlant extends SimBaseNode{
    private float maxCompressionCapacity;
    private float processWaste;
    private float gasConsumption;

    private List<SimBaseNode> nodesBefore;

    public SimCompressingPlant(CompressingPlant compressingPlant){
        super(compressingPlant);
        this.maxCompressionCapacity = compressingPlant.getMaxCompressionCapacity();
        this.processWaste = compressingPlant.getProcessWaste();
        this.gasConsumption = compressingPlant.getGasConsumption();
        nodesBefore = new ArrayList<>();
    }
    @Override
    protected void activeAction(int time){
        float amountToTake =  (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver()).sum();
        float toProcess;

        if(amountToTake >= maxCompressionCapacity){
            toProcess = takeEqualAmounts(nodesBefore,maxCompressionCapacity);
        } else {
            toProcess = calculateAndTakeAll(nodesBefore);
        }

        float loss = ( processWaste + gasConsumption ) /100;

        if(toProcess >= maxCompressionCapacity){
            toDeliver = maxCompressionCapacity * (1 - loss);
        } else {
            toDeliver = toProcess * (1 - loss);
        }
    }
    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(node -> node.getLastSimulatedTime() == time);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode) {
        this.nodesBefore.add(simBaseNode);
    }
}
