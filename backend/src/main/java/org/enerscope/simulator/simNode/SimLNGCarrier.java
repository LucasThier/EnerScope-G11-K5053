package org.enerscope.simulator.simNode;

import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.simulator.FlagOfInactivity;
import org.enerscope.simulator.ResultPerNode;
import org.enerscope.simulator.ToDeliver;

import static org.enerscope.simulator.FlagOfInactivity.OverLifeSpan;

public class SimLNGCarrier extends SimBaseNode{
    private int exportFrequency;
    private float shipCapacity;
    private float fullLoadTime;
    private int timeToDestination;
    private SimSeaportTerminal simSeaportTerminal;
    private boolean isInPort;
    private float amountToTake;
    private float amountInTank;

    public SimLNGCarrier(LNGCarrier lngCarrier){
        super(lngCarrier);
        this.exportFrequency = lngCarrier.getExportFrequency();
        this.shipCapacity = lngCarrier.getShipCapacity();
        this.fullLoadTime = lngCarrier.getFullLoadTime();
        this.timeToDestination = lngCarrier.getTimeToDestination();
        isInPort = false;
        amountToTake = shipCapacity / fullLoadTime;
        amountInTank = 0;
    }

    @Override
    protected void checkInactivity(int time){
        toDeliver = new ToDeliver(0,0);
        int timeOfInactivity = time - timeStartOfInactivity;
        switch (flagOfInactivity){
            case Maintenance: {
                if(timeOfInactivity >= maintenanceDuration){
                    active = true;
                }
                break;
            }
            case OverLifeSpan:{
                if(lifespanInMonths <= time / (24*30)){
                    if(timeOfInactivity >=  timeToDestination + exportFrequency){
                        active = true;
                        amountInTank = 0;
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void activeAction(int time){
        toDeliver = new ToDeliver(0,0);
        if(!isInPort){
            if (simSeaportTerminal.shipAbleToDock()){
                simSeaportTerminal.addBoat();
                isInPort = true;
            }
        } else {
            if (amountInTank >= shipCapacity) {
                flagOfInactivity = OverLifeSpan;
                simSeaportTerminal.restBoat();
                isInPort = false;
            } else {
                maxPossibleProduced += amountToTake;
                float amountAvailable = simSeaportTerminal.getToDeliver().getAmount();
                float capacity = Math.min(shipCapacity - amountInTank , amountToTake);
                if (amountAvailable > capacity ){
                    simSeaportTerminal.deliver(capacity);
                    amountInTank += amountToTake;
                    totalProduced += amountToTake;
                } else {
                    simSeaportTerminal.deliver(amountAvailable);
                    amountInTank += amountAvailable;
                    totalProduced += amountAvailable;
                }
            }
        }
    }

    @Override
    public void addPreviousNode(SimBaseNode simBaseNode){
        simSeaportTerminal = (SimSeaportTerminal) simBaseNode;
    }

    @Override
    public ResultPerNode creatResult() {
        return new ResultPerNode(this.id, LNGCarrier.class.getSimpleName(),totalProduced,totalDeferred,maxPossibleProduced);
    }
}
