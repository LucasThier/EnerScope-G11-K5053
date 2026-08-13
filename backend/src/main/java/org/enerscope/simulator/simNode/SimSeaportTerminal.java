package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.SeaportTerminal;

import java.util.List;

public class SimSeaportTerminal extends SimBaseNode{
    private float intermediateStorage;
    private int shipCapacity;
    private float amountInIntermediateStorage;
    private int amountOfShip;
    private List<SimLiquefactionPlant> simLiquefactionPlants;
    private float totalProcesed;
    private float totalDelivered;

    SimSeaportTerminal(SeaportTerminal seaportTerminal){
        super(seaportTerminal);
        this.intermediateStorage = seaportTerminal.getIntermediateStorage();
        this.shipCapacity = seaportTerminal.getShipCapacity();
        this.amountInIntermediateStorage = 0;
        this.amountOfShip = 0;
    }

    @Override
    protected void activeAction(int time){

        float amountToTake =  (float) simLiquefactionPlants.stream().mapToDouble(SimBaseNode::getToDeliver).sum();
        float toProccess;

        float capacity =intermediateStorage - amountInIntermediateStorage;

        if(amountToTake >= capacity){
            toProccess = takeEqualAmounts(simLiquefactionPlants,capacity);
        } else {
            toProccess = calculateAndTakeAll(simLiquefactionPlants);
        }

        if(toProccess >= capacity){
            toDeliver = capacity;
        } else {
            toDeliver = toProccess;
        }


        totalProcesed += toDeliver;
        totalDelivered += toDeliver;

        if((amountInIntermediateStorage + toDeliver) > intermediateStorage){
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
        totalDelivered += toDeliver;
    }

    public void addBoat(){
        amountOfShip += 1;
    }
    public void restBoat(){
        amountOfShip -= 1;
    }


}
