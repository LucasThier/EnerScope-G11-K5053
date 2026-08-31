package org.enerscope.simulator.simNode;

import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.simulator.ToDeliver;

import java.util.ArrayList;
import java.util.List;

public class SimTreatmentPlant extends SimBaseNode{
    private float maxTreatmentCapacity;
    private float intermediateStorage;
    private List<SimGatheringNetwork> simGatheringNetworks;
    private ToDeliver amountInIntermediateStorage;


    public SimTreatmentPlant(TreatmentPlant treatmentPlant){
        super(treatmentPlant);
        this.maxTreatmentCapacity = treatmentPlant.getMaxTreatmentCapacity();
        this.intermediateStorage = treatmentPlant.getIntermediateStorage();
        this.amountInIntermediateStorage = new ToDeliver(0,0);
        simGatheringNetworks = new ArrayList<>();
    }

    @Override
    protected void activeAction(int time){
        float amountToTake =  (float) simGatheringNetworks.stream().mapToDouble(simGatheringNetwork -> simGatheringNetwork.getToDeliver().getAmount()).sum();
        float capacity = maxTreatmentCapacity + intermediateStorage - amountInIntermediateStorage.getAmount();
        ToDeliver toProcess;

        if(amountToTake >= capacity){
            toProcess = takeEqualAmounts(simGatheringNetworks,capacity);
        } else {
            toProcess = calculateAndTakeAll(simGatheringNetworks);
        }

        toProcess.mix(amountInIntermediateStorage);

        if(toProcess.getAmount() >= maxTreatmentCapacity){
            toDeliver = new ToDeliver(maxTreatmentCapacity,toProcess.getContaminant());
            toDeliver.clean();
            amountInIntermediateStorage.setAmount(toProcess.getAmount() - maxTreatmentCapacity);
        } else {
            toProcess.clean();
            toDeliver = toProcess;
            amountInIntermediateStorage = new ToDeliver(0,0);
        }
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simGatheringNetworks.add((SimGatheringNetwork) simBaseNode);
    }
}
