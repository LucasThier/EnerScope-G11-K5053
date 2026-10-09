package org.enerscope.simulator.simNode;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.simulator.ResultPerNode;
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

    private float totalLost;

    public SimCompressingPlant(CompressingPlant compressingPlant){
        super(compressingPlant);
        this.maxCompressionCapacity = compressingPlant.getMaxCompressionCapacity();
        this.processWaste = compressingPlant.getProcessWaste();
        this.gasConsumption = compressingPlant.getGasConsumption();
        nodesBefore = new ArrayList<>();
        totalLost = 0;
    }
    @Override
    protected void activeAction(int time){
        float amountToTake =  (float) nodesBefore.stream().mapToDouble(simBaseNode -> simBaseNode.getToDeliver().getAmount()).sum();
        ToDeliver toProcess;
        maxPossibleProduced += maxCompressionCapacity;

        if(amountToTake >= maxCompressionCapacity){
            toProcess = takeEqualAmounts(nodesBefore,maxCompressionCapacity);
        } else {
            toProcess = calculateAndTakeAll(nodesBefore);
        }

        float loss = ( processWaste + gasConsumption ) /100;
        float lost;

        if(toProcess.getAmount() >= maxCompressionCapacity){
            lost = maxCompressionCapacity * (loss);
            toDeliver = new ToDeliver(maxCompressionCapacity  - lost,toProcess.getContaminant());
        } else {
            lost = toProcess.getAmount() * (loss);
            toDeliver = new ToDeliver(toProcess.getAmount()  - lost,toProcess.getContaminant());
        }

        totalLost += lost;
    }
    @Override
    public boolean readyToBeProcessed(int time) {
        return nodesBefore.stream().allMatch(node -> node.getLastSimulatedTime() == time);
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode) {
        this.nodesBefore.add(simBaseNode);
    }

    @Override
    public ResultPerNode createResult() {
        ResultPerNode result = new ResultPerNode(this.id, CompressingPlant.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
        result.setExtra(totalLost);
        return result;
    }

    @Override
    public void reset() {
        super.reset();
        totalLost = 0;
    }
}
