package org.enerscope.simulator.simNode;

import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.simulator.FlowCalculator;

import java.util.List;

public class SimTreatmentPlant extends SimBaseNode{
    private float maxTreatmentCapacity;
    private float intermediateStorage;
    private float contaminantWaste;
    private List<SimGatheringNetwork> simGatheringNetworks;

    private float amountInintermediateStorage;
    private float toDeliver;
    private float totalProcesed;
    private float totalDelivered;


    SimTreatmentPlant(TreatmentPlant treatmentPlant){
        super(treatmentPlant);
        this.maxTreatmentCapacity = treatmentPlant.getMaxTreatmentCapacity();
        this.intermediateStorage = treatmentPlant.getIntermediateStorage();
        this.contaminantWaste = treatmentPlant.getContaminantWaste();
        this.amountInintermediateStorage = 0;
    }

    @Override
    protected void activeAction(int time){

        float amountToTake =  (float) simGatheringNetworks.stream().mapToDouble(simGatheringNetwork -> simGatheringNetwork.getToDeliver()).sum();
        float capacity = maxTreatmentCapacity + intermediateStorage - amountInintermediateStorage;
        float toProccess;
        if(amountToTake >= capacity){
             toProccess = new FlowCalculator().takeEqualAmounts(simGatheringNetworks,capacity,SimGatheringNetwork::getToDeliver,SimGatheringNetwork::deliver);
        } else {
            toProccess = new FlowCalculator().calculateAndTakeAll(simGatheringNetworks,SimGatheringNetwork::getToDeliver,SimGatheringNetwork::deliver);
        }

        if(toProccess >= maxTreatmentCapacity){
            toDeliver = maintenanceDuration *  contaminantWaste /100;
            amountInintermediateStorage += toProccess - maxTreatmentCapacity;
        } else {
            toDeliver = toProccess *  contaminantWaste /100;
        }
        totalProcesed += toDeliver;
        totalDelivered += toDeliver;
    }
    @Override
    protected void inactiveAction(int time){
        totalDelivered -= toDeliver;
        toDeliver = 0;
        checkInactivity(time);
    }
}
