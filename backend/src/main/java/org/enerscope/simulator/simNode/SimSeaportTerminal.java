package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.SeaportTerminal;

import java.util.ArrayList;
import java.util.List;

public class SimSeaportTerminal extends SimBaseNode{
    private float intermediateStorage;
    private int shipCapacity;
    private float amountInIntermediateStorage;
    private int amountOfShip;
    private List<SimLiquefactionPlant> simLiquefactionPlants;

    public SimSeaportTerminal(SeaportTerminal seaportTerminal){
        super(seaportTerminal);
        this.intermediateStorage = seaportTerminal.getIntermediateStorage();
        this.shipCapacity = seaportTerminal.getShipCapacity();
        this.amountInIntermediateStorage = 0;
        this.amountOfShip = 0;
        simLiquefactionPlants = new ArrayList<>();
    }

    @Override
    protected void activeAction(int time){

        float amountToTake =  (float) simLiquefactionPlants.stream().mapToDouble(SimBaseNode::getToDeliver).sum();
        float toProcess;

        float capacity = intermediateStorage - amountInIntermediateStorage;

        if(amountToTake >= capacity){
            toProcess = takeEqualAmounts(simLiquefactionPlants,capacity);
        } else {
            toProcess = calculateAndTakeAll(simLiquefactionPlants);
        }

        if(toProcess >= capacity){
            toDeliver = capacity;
        } else {
            toDeliver = toProcess;
        }

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
    }

    public void addBoat(){
        amountOfShip += 1;
    }
    public void restBoat(){
        amountOfShip -= 1;
    }

    public boolean shipAbleToDock(){
        return amountOfShip < shipCapacity;
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simLiquefactionPlants.add((SimLiquefactionPlant) simBaseNode);
    }
}
