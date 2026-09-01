package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.simulator.ResultPerNode;
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

        maxPossibleProduced += capacity;

        if(amountToTake >= capacity){
            toProcess = takeEqualAmounts(simLiquefactionPlants,capacity);
        } else {
            toProcess = calculateAndTakeAll(simLiquefactionPlants);
        }

        totalProduced += toProcess.getAmount();

        amountInIntermediateStorage.setAmount(amountInIntermediateStorage.getAmount() + toProcess.getAmount());
    }

    @Override
    public ToDeliver getToDeliver() {
        return amountInIntermediateStorage;
    }

    @Override
    public ToDeliver deliver(float amount){
        totalDeferred -= amount;
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

    @Override
    public ResultPerNode creatResult() {
        return new ResultPerNode(this.id, SeaportTerminal.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }
}
