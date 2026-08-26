package org.enerscope.simulator.simNode;

import org.enerscope.node.model.extraction.TreatmentPlant;

import java.util.ArrayList;
import java.util.List;

public class SimTreatmentPlant extends SimBaseNode{
    private float maxTreatmentCapacity;
    private float intermediateStorage;
    private float contaminantWaste;
    private List<SimGatheringNetwork> simGatheringNetworks;

    private float amountInintermediateStorage;


    public SimTreatmentPlant(TreatmentPlant treatmentPlant){
        super(treatmentPlant);
        this.maxTreatmentCapacity = treatmentPlant.getMaxTreatmentCapacity();
        this.intermediateStorage = treatmentPlant.getIntermediateStorage();
        this.contaminantWaste = treatmentPlant.getContaminantWaste();
        this.amountInintermediateStorage = 0;
        simGatheringNetworks = new ArrayList<>();
    }

    @Override
    protected void activeAction(int time){
        float amountToTake =  (float) simGatheringNetworks.stream().mapToDouble(simGatheringNetwork -> simGatheringNetwork.getToDeliver()).sum();
        float capacity = maxTreatmentCapacity + intermediateStorage - amountInintermediateStorage;
        float toProccess = amountInintermediateStorage;

        if(amountToTake >= capacity){
             toProccess += takeEqualAmounts(simGatheringNetworks,capacity);
        } else {
            toProccess += calculateAndTakeAll(simGatheringNetworks);
        }

        float wasteFactor = contaminantWaste / 100f;

        if(toProccess >= maxTreatmentCapacity){
            toDeliver = maxTreatmentCapacity * (1 - wasteFactor);
            amountInintermediateStorage = toProccess - maxTreatmentCapacity;
        } else {
            toDeliver = toProccess *  (1 - wasteFactor);
            amountInintermediateStorage = 0;
        }
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simGatheringNetworks.add((SimGatheringNetwork) simBaseNode);
    }
}
