package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.simulator.ToDeliver;

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
        float amountToTake =  (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver().getAmount()).sum();
        ToDeliver toProcess;

        if(amountToTake >= maxCompressionCapacity){
            toProcess = takeEqualAmounts(nodesBefore,maxCompressionCapacity);
        } else {
            toProcess = calculateAndTakeAll(nodesBefore);
        }

        float loss = ( processWaste + gasConsumption ) /100;

        if(toProcess.getAmount() >= maxCompressionCapacity){
            toDeliver = new ToDeliver(maxCompressionCapacity * (1 - loss),toProcess.getContaminant());
        } else {
            toDeliver = new ToDeliver(toProcess.getAmount() * (1 - loss),0);
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
