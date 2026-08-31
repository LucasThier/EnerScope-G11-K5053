package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.simulator.ToDeliver;

import java.util.ArrayList;
import java.util.List;

public class SimSeaportTerminal extends SimBaseNode{
    private float intermediateStorage;
    private int shipCapacity;
    private ToDeliver amountInIntermediateStorage;
    private int amountOfShip;
    private List<SimLiquefactionPlant> simLiquefactionPlants;

    public SimSeaportTerminal(SeaportTerminal seaportTerminal){
        super(seaportTerminal);
        this.intermediateStorage = seaportTerminal.getIntermediateStorage();
        this.shipCapacity = seaportTerminal.getShipCapacity();
        this.amountInIntermediateStorage = new ToDeliver(0,0);
        this.amountOfShip = 0;
        simLiquefactionPlants = new ArrayList<>();
    }

    @Override
    protected void activeAction(int time){

        float amountToTake =  (float) simLiquefactionPlants.stream().mapToDouble(SimBaseNode -> SimBaseNode.getToDeliver().getAmount()).sum();
        ToDeliver toProcess;

        float capacity = intermediateStorage - amountInIntermediateStorage.getAmount();

        if(amountToTake >= capacity){
            toProcess = takeEqualAmounts(simLiquefactionPlants,capacity);
        } else {
            toProcess = calculateAndTakeAll(simLiquefactionPlants);
        }

        if(toProcess.getAmount() >= capacity){
            toDeliver = new ToDeliver(capacity,0);
        } else {
            toDeliver = toProcess;
        }

        if((amountInIntermediateStorage.getAmount() + toDeliver.getAmount()) > intermediateStorage){
            amountInIntermediateStorage.setAmount(intermediateStorage);
        } else {
            amountInIntermediateStorage = toDeliver;
        }
    }

    @Override
    public ToDeliver getToDeliver() {
        return amountInIntermediateStorage;
    }

    @Override
    public ToDeliver deliver(float amount){
        return amountInIntermediateStorage.deliver(amount);
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
